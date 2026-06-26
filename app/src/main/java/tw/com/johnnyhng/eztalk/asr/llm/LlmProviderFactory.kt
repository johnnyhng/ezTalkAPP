package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context

internal class LlmProviderFactory(
    context: Context
) {
    private val appContext = context.applicationContext

    fun createGeminiProvider(model: String): LlmProvider? {
        if (model.isBlank() || model == "none") return null
        return GeminiLlmProvider(
            config = GeminiProviderConfig(model = model),
            accessTokenProvider = GoogleAuthGeminiAccessTokenProvider(appContext)
        )
    }
}
