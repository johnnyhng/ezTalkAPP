package tw.com.johnnyhng.eztalk.asr.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

internal class GoogleSignInManager {
    fun getSignInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun getCurrentSession(context: Context): GoogleAccountSession? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return account.toSession()
    }

    fun getSessionFromIntent(data: Intent?): Result<GoogleAccountSession> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
                ?: return Result.failure(IllegalStateException("Google account was null"))
            Result.success(account.toSession())
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun signOut(
        context: Context,
        onComplete: (Result<Unit>) -> Unit
    ) {
        getSignInClient(context).signOut()
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { error -> onComplete(Result.failure(error)) }
    }

    private fun GoogleSignInAccount.toSession(): GoogleAccountSession {
        return GoogleAccountSession(
            email = email.orEmpty(),
            displayName = displayName
        )
    }
}
