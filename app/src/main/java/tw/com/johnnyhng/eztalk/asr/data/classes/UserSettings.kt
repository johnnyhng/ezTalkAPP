package tw.com.johnnyhng.eztalk.asr.data.classes

import tw.com.johnnyhng.eztalk.asr.BuildConfig
import tw.com.johnnyhng.eztalk.asr.utils.BackendEndpoints

data class UserSettings(
    val userId: String = "default_user",
    val lingerMs: Float = 5000f,
    val partialIntervalMs: Float = 500f,
    val saveVadSegmentsOnly: Boolean = false,
    val inlineEdit: Boolean = false,
    val backendUrl: String = "https://120.126.151.159:56432/api/v2",
    val allowInsecureTls: Boolean = BuildConfig.ALLOW_INSECURE_TLS_BY_DEFAULT,
    val enableTtsFeedback: Boolean = true,
    val selectedModelName: String = "",
    val entryScreenRoute: String = "home",
    val geminiModel: String = "gemini-2.5-flash",
    val preferredAudioInputDeviceId: Int? = null,
    val preferredAudioOutputDeviceId: Int? = null,
    val allowAppAudioCapture: Boolean = false,
    val preferCommunicationDeviceRouting: Boolean = true
) {
    val effectiveRecognitionUrl: String
        get() = BackendEndpoints.processAudio(backendUrl)
}
