package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.flow.collect

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
                FeatureStatus.DOWNLOADING -> SpeakerLocalLlmStatus.Downloading()
                FeatureStatus.UNAVAILABLE -> SpeakerLocalLlmStatus.Unavailable
                else -> SpeakerLocalLlmStatus.Error("Unknown Gemini Nano feature status")
            }
        }.getOrElse { error ->
            SpeakerLocalLlmStatus.Error(
                error.message ?: "Failed to check Gemini Nano availability"
            )
        }
    }

    suspend fun download(
        onStatus: (SpeakerLocalLlmStatus) -> Unit = {}
    ): SpeakerLocalLlmStatus {
        return runCatching {
            var totalBytes: Long? = null
            var latestStatus: SpeakerLocalLlmStatus = SpeakerLocalLlmStatus.Checking

            generativeModel.download().collect { status ->
                latestStatus = when (status) {
                    is DownloadStatus.DownloadStarted -> {
                        totalBytes = status.bytesToDownload
                        SpeakerLocalLlmStatus.Downloading(
                            downloadedBytes = 0L,
                            totalBytes = totalBytes
                        )
                    }

                    is DownloadStatus.DownloadProgress -> {
                        SpeakerLocalLlmStatus.Downloading(
                            downloadedBytes = status.totalBytesDownloaded,
                            totalBytes = totalBytes
                        )
                    }

                    DownloadStatus.DownloadCompleted -> SpeakerLocalLlmStatus.Available

                    is DownloadStatus.DownloadFailed -> SpeakerLocalLlmStatus.Error(
                        status.e.message ?: "Gemini Nano download failed"
                    )
                }
                onStatus(latestStatus)
            }

            latestStatus
        }.getOrElse { error ->
            SpeakerLocalLlmStatus.Error(
                error.message ?: "Failed to start Gemini Nano download"
            )
        }
    }
}
