package tw.com.johnnyhng.eztalk.asr.llm

internal data class LlmUsageMetadata(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null
)
