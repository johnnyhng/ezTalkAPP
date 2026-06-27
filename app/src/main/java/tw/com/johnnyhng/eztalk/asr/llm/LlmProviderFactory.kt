package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings

internal class LlmProviderFactory(
    context: Context
) {
    private val appContext = context.applicationContext
    private val localGemmaModelManager = LocalGemmaModelManager(appContext)

    fun createGeminiProvider(model: String): LlmProvider? {
        if (model.isBlank() || model == "none") return null
        return GeminiLlmProvider(
            config = GeminiProviderConfig(model = model),
            accessTokenProvider = GoogleAuthGeminiAccessTokenProvider(appContext)
        )
    }

    fun createProvider(settings: UserSettings): LlmProvider? {
        val executionMode = SpeakerLlmExecutionMode.fromStorageValue(settings.speakerLlmExecutionMode)
        val provider = when (executionMode) {
            SpeakerLlmExecutionMode.CLOUD -> createGeminiProvider(settings.geminiModel)
            SpeakerLlmExecutionMode.LOCAL_GEMMA_LITERT_LM -> {
                if (settings.selectedLocalGemmaModelName.isBlank()) {
                    safeLogInfo(
                        LLM_LOG_TAG,
                        "Local Gemma model is empty; using Cloud LLM fallback"
                    )
                    createGeminiProvider(settings.geminiModel)
                } else {
                    createLocalGemmaProvider(settings)
                }
            }
            SpeakerLlmExecutionMode.AUTO_LOCAL -> createAutoProvider(settings)
        }
        safeLogInfo(
            LLM_LOG_TAG,
            "LLM provider selected mode=${executionMode.storageValue} provider=${provider?.providerName ?: "none"} " +
                "geminiModel=${settings.geminiModel} localModel=${settings.selectedLocalGemmaModelName} " +
                "localBackend=${settings.localGemmaBackend}"
        )
        return provider
    }

    private fun createAutoProvider(settings: UserSettings): LlmProvider? {
        val localProvider = createLocalGemmaProvider(settings)
        val cloudProvider = createGeminiProvider(settings.geminiModel)
        return when {
            localProvider != null && cloudProvider != null -> FallbackLlmProvider(
                primary = localProvider,
                fallback = cloudProvider,
                fallbackReason = "auto_local"
            )
            localProvider != null -> localProvider
            else -> cloudProvider
        }
    }

    fun createLocalGemmaProvider(settings: UserSettings): LlmProvider? {
        if (settings.selectedLocalGemmaModelName.isBlank()) {
            safeLogInfo(
                LLM_LOG_TAG,
                "Local Gemma provider skipped: empty model selection uses Cloud LLM fallback"
            )
            return null
        }
        val model = localGemmaModelManager.resolveModel(settings.selectedLocalGemmaModelName)
        if (model == null) {
            safeLogWarning(
                LLM_LOG_TAG,
                "Local Gemma provider unavailable model=${settings.selectedLocalGemmaModelName}"
            )
            return null
        }

        val backend = LocalGemmaBackend.fromStorageValue(settings.localGemmaBackend)
        safeLogInfo(
            LLM_LOG_TAG,
            "Local Gemma provider selected model=${model.name} path=${model.path} backend=${backend.storageValue}"
        )
        return LocalGemmaRuntimeManager.getOrCreateProvider(
            context = appContext,
            modelPath = model.path,
            backend = backend
        )
    }
}
