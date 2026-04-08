package tw.com.johnnyhng.eztalk.asr.llm

internal data class GeminiProviderConfig(
    val model: String = "gemini-2.5-flash",
    val endpoint: String = "https://generativelanguage.googleapis.com"
)

internal class GeminiLlmProvider(
    private val config: GeminiProviderConfig = GeminiProviderConfig(),
    private val accessTokenProvider: GeminiAccessTokenProvider? = null
) : LlmProvider {
    override val providerName: String = "gemini"

    override suspend fun generate(
        request: LlmRequest
    ): Result<LlmResponse> {
        val tokenProvider = accessTokenProvider ?: return Result.failure(
            LlmError.ProviderFailure(
                detail = "Gemini OAuth token provider is not configured"
            )
        )

        val token = tokenProvider.fetchToken().getOrElse { error ->
            return Result.failure(
                LlmError.ProviderFailure(
                    detail = "Unable to acquire Gemini OAuth access token",
                    original = error
                )
            )
        }

        return Result.failure(
            LlmError.ProviderFailure(
                detail = buildString {
                    append("Gemini provider is not implemented yet. ")
                    append("OAuth token acquired successfully. ")
                    append("Requested model=")
                    append(request.model.ifBlank { config.model })
                    append(", endpoint=")
                    append(config.endpoint)
                    append(", tokenLength=")
                    append(token.length)
                }
            )
        )
    }
}
