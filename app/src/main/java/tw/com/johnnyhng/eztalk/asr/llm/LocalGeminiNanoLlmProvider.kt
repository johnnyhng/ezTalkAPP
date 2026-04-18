package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.TAG

internal class LocalGeminiNanoLlmProvider(
    context: Context
) : LlmProvider {
    override val providerName: String = "gemini_nano_local"

    @Suppress("unused")
    private val appContext = context.applicationContext
    private val generativeModel = Generation.getClient()

    override suspend fun generate(
        request: LlmRequest
    ): Result<LlmResponse> {
        Log.i(
            TAG,
            "Local Gemini Nano generate start promptLength=${request.userPrompt.length} outputFormat=${request.outputFormat}"
        )

        return withContext(Dispatchers.Default) {
            runCatching {
                val promptText = buildPromptText(request)
                val promptRequest = GenerateContentRequest.Builder(
                    TextPart(promptText)
                ).apply {
                    temperature = 0f
                    topK = 3
                    candidateCount = 1
                    maxOutputTokens = 256
                }.build()

                val response = generativeModel.generateContent(promptRequest)
                val rawText = response.candidates
                    .firstOrNull()
                    ?.text
                    .orEmpty()
                    .trim()

                if (rawText.isBlank()) {
                    throw IllegalArgumentException("Local Gemini Nano returned an empty response")
                }

                LlmResponse(rawText = rawText)
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { error ->
                    Log.w(TAG, "Local Gemini Nano generate failed", error)
                    Result.failure(
                        LlmError.ProviderFailure(
                            detail = error.message ?: "Local Gemini Nano request failed",
                            original = error
                        )
                    )
                }
            )
        }
    }

    private fun buildPromptText(request: LlmRequest): String {
        return buildString {
            request.systemInstruction
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    append(it)
                    append("\n\n")
                }
            append(request.userPrompt.trim())
            request.schemaHint
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    append("\n\nReturn JSON matching this schema hint:\n")
                    append(it)
                }
        }
    }
}
