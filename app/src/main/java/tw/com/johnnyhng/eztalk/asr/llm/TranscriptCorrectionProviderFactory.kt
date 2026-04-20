package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context

internal class TranscriptCorrectionProviderFactory(
    private val appContext: Context
) {
    fun create(model: String): LlmProvider? {
        if (model.isBlank() || model == "none") return null
        return GeminiLlmProvider(
            config = GeminiProviderConfig(model = model),
            accessTokenProvider = GoogleAuthGeminiAccessTokenProvider(appContext)
        )
    }
}
