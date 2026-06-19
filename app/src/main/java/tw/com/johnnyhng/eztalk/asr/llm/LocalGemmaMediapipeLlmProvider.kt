package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.TAG
import java.io.File

internal class LocalGemmaMediapipeLlmProvider(
    private val context: Context
) : LlmProvider {
    override val providerName: String = "gemma_mediapipe_local"

    private val modelPath = File(context.filesDir, "models/gemma2_2b/model.bin").absolutePath

    @Volatile
    private var llmInference: LlmInference? = null

    private fun getOrInitInference(): LlmInference {
        llmInference?.let { return it }
        synchronized(this) {
            llmInference?.let { return it }
            Log.i(TAG, "Initializing MediaPipe LlmInference. Model path: $modelPath")
            
            // Log target execution environment
            Log.i(TAG, "Attempting GPU execution with fallback to CPU. Engine details will be logged by MediaPipe GenAI runtime.")
            
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .setTemperature(0.0f)
                .setTopK(3)
                .build()
            
            val inference = LlmInference.createFromOptions(context, options)
            llmInference = inference
            Log.i(TAG, "MediaPipe LlmInference created successfully.")
            return inference
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
            runCatching {
                val promptText = buildPromptText(request)
                Log.d(TAG, "Formatted local Gemma prompt:\n$promptText")

                val inference = getOrInitInference()
                val rawText = inference.generateResponse(promptText).trim()

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
