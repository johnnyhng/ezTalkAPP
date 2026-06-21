package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.TAG
import java.io.File

internal class LocalGemma4LitertLmLlmProvider(
    private val context: Context
) : LlmProvider {
    override val providerName: String = "gemma4_litertlm_local"

    private val modelPath = File(context.filesDir, "models/gemma4_e2b/model.litertlm").absolutePath

    @Volatile
    private var engine: Engine? = null

    private fun getOrInitEngine(): Engine {
        engine?.let { return it }
        synchronized(this) {
            engine?.let { return it }
            Log.i(TAG, "Initializing LiteRT-LM Engine. Model path: $modelPath")
            val config = EngineConfig(modelPath = modelPath)
            val initializedEngine = Engine(config)
            initializedEngine.initialize()
            engine = initializedEngine
            Log.i(TAG, "LiteRT-LM Engine initialized successfully.")
            return initializedEngine
        }
    }

    override suspend fun generate(
        request: LlmRequest
    ): Result<LlmResponse> {
        Log.i(
            TAG,
            "Local Gemma 4 generate start promptLength=${request.userPrompt.length} outputFormat=${request.outputFormat}"
        )

        return withContext(Dispatchers.Default) {
            runCatching {
                val promptText = buildPromptText(request)
                Log.d(TAG, "Formatted local Gemma 4 prompt:\n$promptText")

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
                    throw IllegalArgumentException("Local Gemma 4 returned an empty response")
                }

                Log.d(TAG, "Local Gemma 4 generated response:\n$rawText")
                LlmResponse(rawText = rawText)
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { error ->
                    Log.w(TAG, "Local Gemma 4 generate failed", error)
                    Result.failure(
                        LlmError.ProviderFailure(
                            detail = error.message ?: "Local Gemma 4 request failed",
                            original = error
                        )
                    )
                }
            )
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
