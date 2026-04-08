package tw.com.johnnyhng.eztalk.asr.llm

internal data class LlmRequest(
    val model: String,
    val systemInstruction: String? = null,
    val userPrompt: String,
    val outputFormat: LlmOutputFormat = LlmOutputFormat.TEXT,
    val schemaHint: String? = null
)
