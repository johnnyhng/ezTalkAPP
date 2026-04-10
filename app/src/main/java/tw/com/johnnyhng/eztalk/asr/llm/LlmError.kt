package tw.com.johnnyhng.eztalk.asr.llm

internal sealed class LlmError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data class Network(val detail: String, val original: Throwable? = null) :
        LlmError(detail, original)

    data class RateLimited(val detail: String) :
        LlmError(detail)

    data class InvalidResponse(val detail: String, val original: Throwable? = null) :
        LlmError(detail, original)

    data class ProviderFailure(val detail: String, val original: Throwable? = null) :
        LlmError(detail, original)
}
