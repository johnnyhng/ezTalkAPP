package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface GeminiAccessTokenProvider {
    suspend fun fetchToken(): Result<String>

    suspend fun invalidateToken(token: String): Result<Unit>
}

internal class GoogleAuthGeminiAccessTokenProvider(
    private val appContext: Context
) : GeminiAccessTokenProvider {
    override suspend fun fetchToken(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val account = GoogleSignIn.getLastSignedInAccount(appContext)?.account
                ?: throw IllegalStateException("No signed-in Google account available")

            GoogleAuthUtil.getToken(
                appContext,
                account,
                GEMINI_OAUTH_SCOPE
            )
        }
    }

    override suspend fun invalidateToken(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            GoogleAuthUtil.invalidateToken(appContext, token)
        }
    }

    private companion object {
        const val GEMINI_OAUTH_SCOPE =
            "oauth2:https://www.googleapis.com/auth/generative-language.retriever"
    }
}
