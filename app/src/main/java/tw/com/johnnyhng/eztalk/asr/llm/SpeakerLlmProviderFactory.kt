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
        localGemmaModelName: String = "",
        localGemmaBackend: String = LocalGemmaBackend.AUTO.storageValue
    ): SpeakerLlmRuntimeSelection {
        val localStatus = SpeakerLocalLlmAvailabilityChecker(appContext).check()
        val cloudProvider = geminiModel?.let {
            GeminiLlmProvider(
                accessTokenProvider = GoogleAuthGeminiAccessTokenProvider(appContext)
            )
        }
        val localGemmaProvider = createLocalGemmaProvider(
            modelName = localGemmaModelName,
            backendValue = localGemmaBackend
        )

        return when (executionMode) {
            SpeakerLlmExecutionMode.AUTO_LOCAL -> {
                val localProvider = if (localStatus is SpeakerLocalLlmStatus.Available) {
                    LocalGeminiNanoLlmProvider(appContext)
                } else {
                    null
                }
                SpeakerLlmRuntimeSelection(
                    provider = localGemmaProvider ?: localProvider ?: cloudProvider,
                    sourceLabel = when {
                        localGemmaProvider != null -> "local_gemma_litertlm"
                        localProvider != null -> "local_gemini_nano"
                        else -> "cloud"
                    },
                    localStatus = localStatus,
                    executionMode = executionMode
                )
            }

            SpeakerLlmExecutionMode.LOCAL_GEMMA_LITERT_LM -> {
                if (localGemmaModelName.isBlank()) {
                    return SpeakerLlmRuntimeSelection(
                        provider = cloudProvider,
                        sourceLabel = "cloud",
                        localStatus = localStatus,
                        executionMode = executionMode
                    )
                }
                SpeakerLlmRuntimeSelection(
                    provider = localGemmaProvider,
                    sourceLabel = if (localGemmaProvider != null) {
                        "local_gemma_litertlm"
                    } else {
                        "local_gemma_unavailable"
                    },
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

    private fun createLocalGemmaProvider(
        modelName: String,
        backendValue: String
    ): LlmProvider? {
        if (modelName.isBlank()) {
            safeLogInfo(LLM_LOG_TAG, "Speaker Local Gemma skipped: empty model selection uses Cloud LLM fallback")
            return null
        }
        val model = LocalGemmaModelManager(appContext).resolveModel(modelName)
        if (model == null) {
            safeLogWarning(LLM_LOG_TAG, "Speaker Local Gemma unavailable model=$modelName")
            return null
        }
        val backend = LocalGemmaBackend.fromStorageValue(backendValue)
        return LocalGemmaRuntimeManager.getOrCreateProvider(
            context = appContext,
            modelPath = model.path,
            backend = backend
        )
    }
}
