package tw.com.johnnyhng.eztalk.asr.llm

internal sealed interface SpeakerLocalLlmStatus {
    data object Checking : SpeakerLocalLlmStatus
    data object Available : SpeakerLocalLlmStatus
    data object Downloadable : SpeakerLocalLlmStatus
    data object Downloading : SpeakerLocalLlmStatus
    data object Unavailable : SpeakerLocalLlmStatus
    data class Error(val message: String) : SpeakerLocalLlmStatus
}
