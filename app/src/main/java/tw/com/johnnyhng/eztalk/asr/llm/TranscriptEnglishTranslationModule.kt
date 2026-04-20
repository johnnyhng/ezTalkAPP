package tw.com.johnnyhng.eztalk.asr.llm

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.prompt.TranscriptEnglishTranslationPromptBuilder

internal class TranscriptEnglishTranslationModule(
    private val llmProvider: LlmProvider? = null,
    private val llmModel: String = "gemini-2.5-flash",
    private val promptBuilder: TranscriptEnglishTranslationPromptBuilder =
        TranscriptEnglishTranslationPromptBuilder()
) {
    suspend fun translate(
        sourceText: String
    ): Result<String?> {
        val provider = llmProvider ?: return Result.success(null)
        val sanitizedText = sourceText.trim()
        if (sanitizedText.isBlank()) return Result.success(null)
        if (llmModel.isBlank() || llmModel == "none") return Result.success(null)

        val prompt = promptBuilder.build(sanitizedText)
        val request = LlmRequest(
            model = llmModel,
            systemInstruction = prompt.systemInstruction,
            userPrompt = prompt.userPrompt,
            outputFormat = LlmOutputFormat.JSON,
            schemaHint = prompt.expectedResponseSchema
        )

        Log.i(
            TAG,
            "Transcript English translation request built model=$llmModel sourceLength=${sanitizedText.length}"
        )

        return provider.generate(request).map { response ->
            parseResponse(response)
        }
    }

    private fun parseResponse(response: LlmResponse): String? {
        val json = extractJsonObject(response.rawText) ?: return null
        return json.optString("translated_text").trim().ifBlank { null }
    }

    private fun extractJsonObject(rawText: String): JSONObject? {
        val trimmed = rawText.trim()
        return tryParseJsonObject(trimmed)
            ?: tryParseJsonObject(
                trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            )
            ?: extractFirstObjectCandidate(trimmed)?.let(::tryParseJsonObject)
    }

    private fun tryParseJsonObject(candidate: String): JSONObject? {
        if (candidate.isBlank()) return null
        return try {
            JSONObject(candidate)
        } catch (_: JSONException) {
            null
        }
    }

    private fun extractFirstObjectCandidate(rawText: String): String? {
        val start = rawText.indexOf('{')
        val end = rawText.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return rawText.substring(start, end + 1)
    }
}
