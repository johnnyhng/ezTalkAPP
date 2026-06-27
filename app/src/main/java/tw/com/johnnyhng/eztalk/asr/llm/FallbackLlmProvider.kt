package tw.com.johnnyhng.eztalk.asr.llm

internal class FallbackLlmProvider(
    private val primary: LlmProvider,
    private val fallback: LlmProvider,
    private val fallbackReason: String
) : LlmProvider {
    override val providerName: String = "${primary.providerName}_with_${fallback.providerName}_fallback"

    override suspend fun generate(request: LlmRequest): Result<LlmResponse> {
        val primaryResult = primary.generate(request)
        if (primaryResult.isSuccess) {
            return primaryResult
        }

        val error = primaryResult.exceptionOrNull()
        safeLogWarning(
            LLM_LOG_TAG,
            "LLM primary provider failed; falling back to cloud reason=$fallbackReason error=${error?.message}",
            error
        )
        return fallback.generate(request)
    }
}
