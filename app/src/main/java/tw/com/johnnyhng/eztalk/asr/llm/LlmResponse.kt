package tw.com.johnnyhng.eztalk.asr.llm

internal data class LlmResponse(
    val rawText: String,
    val finishReason: String? = null,
    val usage: LlmUsageMetadata? = null
)
