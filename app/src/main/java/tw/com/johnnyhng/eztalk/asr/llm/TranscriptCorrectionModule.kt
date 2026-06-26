package tw.com.johnnyhng.eztalk.asr.llm

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.prompt.TranscriptCorrectionPromptBuilder

internal data class TranscriptCorrectionResult(
    val correctedText: String,
    val confidence: Float,
    val reasoning: String?
)

internal class TranscriptCorrectionModule(
    private val llmProvider: LlmProvider? = null,
    private val llmModel: String = "gemini-2.5-flash",
    private val promptBuilder: TranscriptCorrectionPromptBuilder = TranscriptCorrectionPromptBuilder()
) {
    suspend fun correct(
        utteranceVariants: List<String>,
        contextLines: List<String>
    ): Result<TranscriptCorrectionResult?> {
        val provider = llmProvider ?: run {
            Log.w(LLM_LOG_TAG, "Transcript correction skipped: LLM provider is not ready")
            return Result.success(null)
        }
        val sanitizedVariants = utteranceVariants
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (sanitizedVariants.isEmpty()) {
            Log.i(LLM_LOG_TAG, "Transcript correction skipped: no utterance variants")
            return Result.success(null)
        }
        if (llmModel.isBlank() || llmModel == "none") {
            Log.i(LLM_LOG_TAG, "Transcript correction skipped: llmModel=$llmModel")
            return Result.success(null)
        }

        val prompt = promptBuilder.build(
            utteranceVariants = sanitizedVariants,
            contextLines = contextLines
        )
        val request = LlmRequest(
            model = llmModel,
            systemInstruction = prompt.systemInstruction,
            userPrompt = prompt.userPrompt,
            outputFormat = LlmOutputFormat.JSON,
            schemaHint = prompt.expectedResponseSchema
        )

        Log.i(
            TAG,
            "Transcript correction request built model=$llmModel variants=${sanitizedVariants.size}:$sanitizedVariants contextLines=${contextLines.size}"
        )
        Log.i(
            LLM_LOG_TAG,
            "Transcript correction variants entering LLM request model=$llmModel " +
                "variants=${sanitizedVariants.size}:$sanitizedVariants contextLines=${contextLines.size}:$contextLines"
        )

        return provider.generate(request).map { response ->
            Log.d(
                LLM_LOG_TAG,
                "Transcript correction raw response preview=${response.rawText.take(500)}"
            )
            parseResponse(response)
        }
    }

    private fun parseResponse(response: LlmResponse): TranscriptCorrectionResult? {
        val json = extractJsonObject(response.rawText) ?: run {
            Log.w(LLM_LOG_TAG, "Transcript correction skipped: response did not contain a JSON object")
            return null
        }
        val correctedText = json.optString("corrected_text").trim()
        val confidence = json.optFlexibleDouble("confidence")?.toFloat() ?: 0f
        val reasoning = json.optString("reasoning").takeIf { it.isNotBlank() }

        Log.i(
            TAG,
            "Transcript correction payload parsed confidence=$confidence correctedLength=${correctedText.length} reasoning=${reasoning.orEmpty()}"
        )
        Log.i(
            LLM_LOG_TAG,
            "Transcript correction parsed correctedText=$correctedText confidence=$confidence reasoning=${reasoning.orEmpty()}"
        )

        if (correctedText.isBlank()) {
            Log.i(LLM_LOG_TAG, "Transcript correction skipped: corrected_text is blank")
            return null
        }
        if (confidence < AUTO_APPLY_CONFIDENCE_THRESHOLD) {
            Log.i(
                LLM_LOG_TAG,
                "Transcript correction skipped: confidence=$confidence threshold=$AUTO_APPLY_CONFIDENCE_THRESHOLD"
            )
            return null
        }

        return TranscriptCorrectionResult(
            correctedText = correctedText,
            confidence = confidence,
            reasoning = reasoning
        )
    }

    private fun extractJsonObject(rawText: String): JSONObject? {
        val trimmed = rawText.trim()
        return tryParseJsonObject(trimmed)
            ?: tryParseJsonObject(trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
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

    private fun JSONObject.optFlexibleDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    companion object {
        private const val AUTO_APPLY_CONFIDENCE_THRESHOLD = 0.85f
    }
}
