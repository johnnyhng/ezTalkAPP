package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context

internal data class SpeakerLlmRuntimeSelection(
    val provider: LlmProvider?,
    val sourceLabel: String,
    val localStatus: SpeakerLocalLlmStatus,
    val executionMode: SpeakerLlmExecutionMode
)

internal class SpeakerLlmProviderFactory(
    context: Context
) {
    private val appContext = context.applicationContext

    suspend fun create(
        geminiModel: String?,
        executionMode: SpeakerLlmExecutionMode
    ): SpeakerLlmRuntimeSelection {
        val localStatus = SpeakerLocalLlmAvailabilityChecker(appContext).check()
        val cloudProvider = geminiModel?.let {
            GeminiLlmProvider(
                accessTokenProvider = GoogleAuthGeminiAccessTokenProvider(appContext)
            )
        }

        return when (executionMode) {
            SpeakerLlmExecutionMode.AUTO_LOCAL -> {
                val localProvider = if (localStatus is SpeakerLocalLlmStatus.Available) {
                    LocalGeminiNanoLlmProvider(appContext)
                } else {
                    null
                }
                SpeakerLlmRuntimeSelection(
                    provider = localProvider ?: cloudProvider,
                    sourceLabel = if (localProvider != null) "local" else "cloud",
                    localStatus = localStatus,
                    executionMode = executionMode
                )
            }

            SpeakerLlmExecutionMode.CLOUD -> {
                SpeakerLlmRuntimeSelection(
                    provider = cloudProvider,
                    sourceLabel = "cloud",
                    localStatus = localStatus,
                    executionMode = executionMode
                )
            }
        }
    }
}
