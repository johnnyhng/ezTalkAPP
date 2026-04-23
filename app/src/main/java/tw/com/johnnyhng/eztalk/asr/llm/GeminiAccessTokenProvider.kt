package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.TAG

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
            Log.i(TAG, "Gemini OAuth token request started for account=${account.name}")
            val startTime = System.currentTimeMillis()

            GoogleAuthUtil.getToken(
                appContext,
                account,
                GEMINI_OAUTH_SCOPE
            ).also {
                val duration = System.currentTimeMillis() - startTime
                Log.i(TAG, "Gemini OAuth token request succeeded tokenLength=${it.length} duration=${duration}ms")
            }
        }.onFailure { error ->
            Log.w(TAG, "Gemini OAuth token request failed", error)
        }
    }

    override suspend fun invalidateToken(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Log.i(TAG, "Gemini OAuth invalidating token tokenLength=${token.length}")
            GoogleAuthUtil.invalidateToken(appContext, token)
        }.onSuccess {
            Log.i(TAG, "Gemini OAuth token invalidation succeeded")
        }.onFailure { error ->
            Log.w(TAG, "Gemini OAuth token invalidation failed", error)
        }
    }

    private companion object {
        const val GEMINI_OAUTH_SCOPE =
            "oauth2:https://www.googleapis.com/auth/generative-language.retriever"
    }
}
