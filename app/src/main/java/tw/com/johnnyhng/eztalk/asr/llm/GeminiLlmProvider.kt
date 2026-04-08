package tw.com.johnnyhng.eztalk.asr.llm

internal data class GeminiProviderConfig(
    val model: String = "gemini-2.5-flash",
    val apiKey: String? = null,
    val endpoint: String = "https://generativelanguage.googleapis.com"
)

internal class GeminiLlmProvider(
    private val config: GeminiProviderConfig = GeminiProviderConfig()
) : LlmProvider {
    override val providerName: String = "gemini"

    override suspend fun generate(
        request: LlmRequest
    ): Result<LlmResponse> {
        return Result.failure(
            LlmError.ProviderFailure(
                detail = buildString {
                    append("Gemini provider is not implemented yet. ")
                    append("Requested model=")
                    append(request.model.ifBlank { config.model })
                }
            )
        )
    }
}
