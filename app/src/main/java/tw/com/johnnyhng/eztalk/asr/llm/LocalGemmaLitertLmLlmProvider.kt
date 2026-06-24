package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.TAG

internal class LocalGemmaLitertLmLlmProvider(
    private val context: Context,
    private val modelPath: String,
    private val localGemmaBackend: String = "npu_gpu_cpu"
) : LlmProvider {
    override val providerName: String = "local_gemma_litertlm"

    companion object {
        init {
            try {
                System.loadLibrary("litertlm_jni")
                Log.i(TAG, "Successfully loaded litertlm_jni explicitly.")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to load litertlm_jni explicitly: ${e.message}")
            }
        }
    }

    @Volatile
    private var engine: Engine? = null

    private val generationMutex = Mutex()

    private fun getOrInitEngine(): Engine {
        engine?.let { return it }
        synchronized(this) {
            engine?.let { return it }
            Log.i(TAG, "Initializing LiteRT-LM Engine. Model path: $modelPath, Mode: $localGemmaBackend")
            var initializedEngine: Engine? = null
            var firstError: Throwable? = null

            val backendsToTry = when (localGemmaBackend) {
                "npu_gpu_cpu" -> listOf("npu", "gpu", "cpu")
                "gpu_cpu" -> listOf("gpu", "cpu")
                "npu" -> listOf("npu")
                "gpu" -> listOf("gpu")
                "cpu" -> listOf("cpu")
                else -> listOf("npu", "gpu", "cpu")
            }

            for (backendType in backendsToTry) {
                try {
                    if (backendType != "npu" && modelPath.contains("Google_Tensor", ignoreCase = true)) {
                        Log.w(TAG, "WARNING: Attempting to run a Tensor-compiled model ($modelPath) on non-NPU backend ($backendType). This will likely fail with 'Input tensor not found'. Please download and select the standard, uncompiled model (e.g. gemma-4-E2B-it-litert-lm) for GPU/CPU usage.")
                    }
                    when (backendType) {
                        "npu" -> {
                            val nativeLibDir = context.applicationInfo.nativeLibraryDir
                            val hasDispatchLib = try {
                                val libDir = java.io.File(nativeLibDir)
                                libDir.exists() && libDir.listFiles()?.any {
                                    it.name.startsWith("libLiteRtDispatch_") && it.name.endsWith(".so")
                                } == true
                            } catch (e: Throwable) {
                                false
                            }

                            if (hasDispatchLib) {
                                Log.i(TAG, "Attempting to initialize LiteRT-LM Engine with NPU backend")
                                val config = EngineConfig(modelPath = modelPath, backend = Backend.NPU(nativeLibDir))
                                val engineInstance = Engine(config)
                                engineInstance.initialize()
                                initializedEngine = engineInstance
                                Log.i(TAG, "LiteRT-LM Engine initialized successfully with NPU backend.")
                                break
                            } else {
                                Log.w(TAG, "No libLiteRtDispatch_*.so found in nativeLibraryDir ($nativeLibDir). Skipping NPU backend.")
                                if (localGemmaBackend == "npu") {
                                    throw IllegalStateException("NPU backend requested but no libLiteRtDispatch_*.so found.")
                                }
                            }
                        }
                        "gpu" -> {
                            Log.i(TAG, "Attempting to initialize LiteRT-LM Engine with GPU backend")
                            val config = EngineConfig(modelPath = modelPath, backend = Backend.GPU())
                            val engineInstance = Engine(config)
                            engineInstance.initialize()
                            initializedEngine = engineInstance
                            Log.i(TAG, "LiteRT-LM Engine initialized successfully with GPU backend.")
                            break
                        }
                        "cpu" -> {
                            Log.i(TAG, "Attempting to initialize LiteRT-LM Engine with CPU backend")
                            val config = EngineConfig(modelPath = modelPath, backend = Backend.CPU())
                            val engineInstance = Engine(config)
                            engineInstance.initialize()
                            initializedEngine = engineInstance
                            Log.i(TAG, "LiteRT-LM Engine initialized successfully with CPU backend.")
                            break
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to initialize LiteRT-LM Engine with backend $backendType", e)
                    if (firstError == null) firstError = e
                }
            }

            if (initializedEngine == null) {
                throw firstError ?: IllegalStateException("Failed to initialize any LiteRT-LM backend")
            }

            engine = initializedEngine
            return initializedEngine
        }
    }

    override suspend fun generate(
        request: LlmRequest
    ): Result<LlmResponse> {
        Log.i(
            TAG,
            "Local Gemma generate start promptLength=${request.userPrompt.length} outputFormat=${request.outputFormat}"
        )

        return withContext(Dispatchers.Default) {
            generationMutex.withLock {
                // LiteRT-LM's Flow cancellation does not cancel the native inference. Keep the
                // conversation alive until its native callback finishes, then close it safely.
                withContext(NonCancellable) {
                    runCatching {
                        val promptText = buildPromptText(request)
                        Log.d(TAG, "Formatted local Gemma prompt:\n$promptText")

                        val engineInstance = getOrInitEngine()
                        val conversation = engineInstance.createConversation()
                        val responseBuilder = StringBuilder()

                        try {
                            val responseFlow = conversation.sendMessageAsync(promptText)
                            responseFlow.collect { token ->
                                responseBuilder.append(token)
                            }
                        } finally {
                            try {
                                conversation.close()
                            } catch (e: Exception) {
                                Log.w(TAG, "Error closing conversation", e)
                            }
                        }

                        val rawText = responseBuilder.toString().trim()
                        if (rawText.isBlank()) {
                            throw IllegalArgumentException("Local Gemma returned an empty response")
                        }

                        Log.d(TAG, "Local Gemma generated response:\n$rawText")
                        LlmResponse(rawText = rawText)
                    }.fold(
                        onSuccess = { Result.success(it) },
                        onFailure = { error ->
                            Log.w(TAG, "Local Gemma generate failed", error)
                            Result.failure(
                                LlmError.ProviderFailure(
                                    detail = error.message ?: "Local Gemma request failed",
                                    original = error
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    internal fun buildPromptText(request: LlmRequest): String {
        return buildString {
            append("<start_of_turn>user\n")
            request.systemInstruction?.trim()?.takeIf { it.isNotBlank() }?.let {
                append(it)
                append("\n\n")
            }
            append(request.userPrompt.trim())
            request.schemaHint?.trim()?.takeIf { it.isNotBlank() }?.let {
                append("\n\nReturn JSON matching this schema hint:\n")
                append(it)
            }
            append("<end_of_turn>\n<start_of_turn>model\n")
        }
    }
}
