package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class LocalGemmaLitertLmLlmProvider(
    context: Context,
    private val modelPath: String,
    private val backend: LocalGemmaBackend = LocalGemmaBackend.AUTO
) : LlmProvider {
    override val providerName: String = "local_gemma_litertlm"

    private val appContext = context.applicationContext
    private val generationMutex = Mutex()

    @Volatile
    private var engine: Engine? = null

    suspend fun warmUp(): Result<Unit> {
        return withContext(Dispatchers.Default) {
            runCatching {
                getOrInitEngine()
                Unit
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { error ->
                    safeLogWarning(LLM_LOG_TAG, "Local Gemma warm-up failed", error)
                    Result.failure(
                        LlmError.ProviderFailure(
                            detail = error.message ?: "Local Gemma warm-up failed",
                            original = error
                        )
                    )
                }
            )
        }
    }

    override suspend fun generate(
        request: LlmRequest
    ): Result<LlmResponse> {
        safeLogInfo(
            LLM_LOG_TAG,
            "Local Gemma generate start modelPath=$modelPath backend=${backend.storageValue} " +
                "promptLength=${request.userPrompt.length} outputFormat=${request.outputFormat}"
        )

        return withContext(Dispatchers.Default) {
            generationMutex.withLock {
                withContext(NonCancellable) {
                    runCatching {
                        val promptText = buildPromptText(request)
                        safeLogDebug(LLM_LOG_TAG, "Formatted local Gemma prompt:\n$promptText")

                        val conversation = getOrInitEngine().createConversation()
                        val responseBuilder = StringBuilder()
                        try {
                            conversation.sendMessageAsync(promptText).collect { token ->
                                responseBuilder.append(token)
                            }
                        } finally {
                            runCatching { conversation.close() }
                                .onFailure { safeLogWarning(LLM_LOG_TAG, "Error closing Local Gemma conversation", it) }
                        }

                        val rawText = responseBuilder.toString().trim()
                        if (rawText.isBlank()) {
                            throw IllegalArgumentException("Local Gemma returned an empty response")
                        }

                        safeLogDebug(LLM_LOG_TAG, "Local Gemma generated response:\n$rawText")
                        LlmResponse(rawText = rawText)
                    }.fold(
                        onSuccess = { Result.success(it) },
                        onFailure = { error ->
                            safeLogWarning(LLM_LOG_TAG, "Local Gemma generate failed", error)
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

    private fun getOrInitEngine(): Engine {
        engine?.let { return it }
        synchronized(this) {
            engine?.let { return it }

            safeLogInfo(
                LLM_LOG_TAG,
                "Initializing LiteRT-LM Engine modelPath=$modelPath backend=${backend.storageValue}"
            )

            var firstError: Throwable? = null
            for (candidateBackend in backend.candidates()) {
                val initialized = runCatching {
                    createEngine(candidateBackend)
                }.onFailure { error ->
                    safeLogWarning(
                        LLM_LOG_TAG,
                        "Failed to initialize LiteRT-LM Engine backend=${candidateBackend.storageValue}",
                        error
                    )
                    if (firstError == null) {
                        firstError = error
                    }
                }.getOrNull()

                if (initialized != null) {
                    engine = initialized
                    safeLogInfo(
                        LLM_LOG_TAG,
                        "LiteRT-LM Engine initialized backend=${candidateBackend.storageValue}"
                    )
                    return initialized
                }
            }

            throw firstError ?: IllegalStateException("Failed to initialize any LiteRT-LM backend")
        }
    }

    private fun createEngine(candidateBackend: LocalGemmaBackend): Engine {
        rejectUnsupportedBackendModelMismatch(candidateBackend)
        val engineConfig = when (candidateBackend) {
            LocalGemmaBackend.NPU -> {
                val nativeLibDir = appContext.applicationInfo.nativeLibraryDir
                if (!hasGoogleTensorDispatchLibrary(nativeLibDir)) {
                    throw IllegalStateException("NPU backend requested but libLiteRtDispatch_GoogleTensor.so is missing")
                }
                EngineConfig(modelPath = modelPath, backend = Backend.NPU(nativeLibDir))
            }

            LocalGemmaBackend.GPU -> EngineConfig(modelPath = modelPath, backend = Backend.GPU())
            LocalGemmaBackend.CPU -> EngineConfig(modelPath = modelPath, backend = Backend.CPU())
            LocalGemmaBackend.AUTO -> error("AUTO must be expanded before engine creation")
        }

        return Engine(engineConfig).also { it.initialize() }
    }

    private fun hasGoogleTensorDispatchLibrary(nativeLibDir: String): Boolean {
        val libDir = File(nativeLibDir)
        return libDir.exists() &&
            libDir.listFiles()?.any { it.name == GOOGLE_TENSOR_DISPATCH_LIB } == true
    }

    private fun rejectUnsupportedBackendModelMismatch(candidateBackend: LocalGemmaBackend) {
        if (
            candidateBackend != LocalGemmaBackend.NPU &&
            isTensorCompiledModel()
        ) {
            throw IllegalArgumentException(
                "Tensor-compiled Local Gemma model requires NPU backend. " +
                    "Selected backend=${candidateBackend.storageValue}. " +
                    "Use NPU for Google_Tensor/G5 models, or download a non-Tensor LiteRT-LM model for GPU/CPU."
            )
        }
    }

    private fun LocalGemmaBackend.candidates(): List<LocalGemmaBackend> {
        return when (this) {
            LocalGemmaBackend.AUTO -> if (isTensorCompiledModel()) {
                safeLogInfo(
                    LLM_LOG_TAG,
                    "Local Gemma auto backend restricted to NPU for Tensor-compiled modelPath=$modelPath"
                )
                listOf(LocalGemmaBackend.NPU)
            } else {
                listOf(LocalGemmaBackend.NPU, LocalGemmaBackend.GPU, LocalGemmaBackend.CPU)
            }
            LocalGemmaBackend.NPU -> listOf(LocalGemmaBackend.NPU)
            LocalGemmaBackend.GPU -> listOf(LocalGemmaBackend.GPU)
            LocalGemmaBackend.CPU -> listOf(LocalGemmaBackend.CPU)
        }
    }

    private fun isTensorCompiledModel(): Boolean {
        return modelPath.contains("Google_Tensor", ignoreCase = true) ||
            modelPath.contains("Tensor_G", ignoreCase = true)
    }

    private companion object {
        const val GOOGLE_TENSOR_DISPATCH_LIB = "libLiteRtDispatch_GoogleTensor.so"

        init {
            runCatching {
                System.loadLibrary("litertlm_jni")
            }.onSuccess {
                safeLogInfo(LLM_LOG_TAG, "Loaded litertlm_jni explicitly")
            }.onFailure {
                safeLogWarning(LLM_LOG_TAG, "Failed to load litertlm_jni explicitly: ${it.message}")
            }
        }
    }
}
