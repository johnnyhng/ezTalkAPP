package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation

internal class SpeakerLocalLlmAvailabilityChecker(
    context: Context
) {
    @Suppress("unused")
    private val appContext = context.applicationContext
    private val generativeModel = Generation.getClient()

    suspend fun check(): SpeakerLocalLlmStatus {
        return runCatching {
            when (generativeModel.checkStatus()) {
                FeatureStatus.AVAILABLE -> SpeakerLocalLlmStatus.Available
                FeatureStatus.DOWNLOADABLE -> SpeakerLocalLlmStatus.Downloadable
                FeatureStatus.DOWNLOADING -> SpeakerLocalLlmStatus.Downloading
                FeatureStatus.UNAVAILABLE -> SpeakerLocalLlmStatus.Unavailable
                else -> SpeakerLocalLlmStatus.Error("Unknown Gemini Nano feature status")
            }
        }.getOrElse { error ->
            SpeakerLocalLlmStatus.Error(
                error.message ?: "Failed to check Gemini Nano availability"
            )
        }
    }
}
