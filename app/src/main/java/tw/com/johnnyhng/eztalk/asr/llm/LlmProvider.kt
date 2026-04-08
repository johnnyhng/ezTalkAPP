package tw.com.johnnyhng.eztalk.asr.llm

internal interface LlmProvider {
    val providerName: String

    suspend fun generate(
        request: LlmRequest
    ): Result<LlmResponse>
}
