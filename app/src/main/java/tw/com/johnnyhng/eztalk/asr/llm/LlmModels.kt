package tw.com.johnnyhng.eztalk.asr.llm

internal enum class LlmOutputFormat {
    TEXT,
    JSON
}

internal data class LlmUsageMetadata(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null
)

internal data class LlmRequest(
    val model: String,
    val systemInstruction: String? = null,
    val userPrompt: String,
    val outputFormat: LlmOutputFormat = LlmOutputFormat.TEXT,
    val schemaHint: String? = null
)

internal data class LlmResponse(
    val rawText: String,
    val finishReason: String? = null,
    val usage: LlmUsageMetadata? = null
)
