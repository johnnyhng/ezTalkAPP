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
        return when (executionMode) {
            SpeakerLlmExecutionMode.CLOUD -> createGeminiProvider(settings.geminiModel)
            SpeakerLlmExecutionMode.LOCAL_GEMMA_LITERT_LM -> createLocalGemmaProvider(settings)
            SpeakerLlmExecutionMode.AUTO_LOCAL -> createLocalGemmaProvider(settings)
                ?: createGeminiProvider(settings.geminiModel)
        }
    }

    fun createLocalGemmaProvider(settings: UserSettings): LlmProvider? {
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
