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
        executionMode: SpeakerLlmExecutionMode,
        selectedLocalGemmaModelName: String
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

            SpeakerLlmExecutionMode.LOCAL_GEMMA_LITERT_LM -> {
                val modelManager = LocalGemmaModelManager(appContext)
                val modelName = selectedLocalGemmaModelName.takeIf { it.isNotBlank() }
                    ?: modelManager.listModels().firstOrNull()?.name
                    ?: ""
                val gemmaStatus = if (modelName.isNotBlank()) {
                    modelManager.check(modelName)
                } else {
                    SpeakerLocalLlmStatus.Downloadable
                }
                val gemmaProvider = if (gemmaStatus is SpeakerLocalLlmStatus.Available && modelName.isNotBlank()) {
                    LocalGemmaLitertLmLlmProvider(appContext, modelManager.getModelFile(modelName).absolutePath)
                } else {
                    null
                }
                SpeakerLlmRuntimeSelection(
                    provider = gemmaProvider ?: cloudProvider,
                    sourceLabel = if (gemmaProvider != null) "local_gemma" else "cloud",
                    localStatus = gemmaStatus,
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
