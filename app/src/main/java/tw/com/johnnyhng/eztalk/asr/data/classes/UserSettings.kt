package tw.com.johnnyhng.eztalk.asr.data.classes

import tw.com.johnnyhng.eztalk.asr.utils.BackendEndpoints

data class UserSettings(
    val userId: String = "default_user",
    val lingerMs: Float = 1000f,
    val partialIntervalMs: Float = 500f,
    val saveVadSegmentsOnly: Boolean = false,
    val inlineEdit: Boolean = true,
    val backendUrl: String = "https://120.126.151.159:56432/api/v2",
    val enableTtsFeedback: Boolean = false,
    val selectedModelName: String = ""
) {
    val effectiveRecognitionUrl: String
        get() = BackendEndpoints.processAudio(backendUrl)
}
