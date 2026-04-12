package tw.com.johnnyhng.eztalk.asr.auth

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.tasks.await
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

internal class GoogleSignInManager {
    fun getSignInClient(context: Context): GoogleSignInClient {
        val optionsBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
        resolveWebClientId(context)?.let(optionsBuilder::requestIdToken)
        val options = optionsBuilder.build()
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
        FirebaseAuth.getInstance().signOut()
        getSignInClient(context).signOut()
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { error -> onComplete(Result.failure(error)) }
    }

    suspend fun signInToFirebase(session: GoogleAccountSession): Result<Unit> {
        val idToken = session.idToken
            ?: return Result.failure(IllegalStateException("Google ID token unavailable"))
        return runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential).await()
            Unit
        }
    }

    suspend fun restoreFirebaseSession(context: Context): Result<Unit> {
        val session = getCurrentSession(context)
            ?: return Result.failure(IllegalStateException("No Google session available"))
        return signInToFirebase(session)
    }

    private fun resolveWebClientId(context: Context): String? {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resourceId == 0) return null
        return context.getString(resourceId).takeIf { it.isNotBlank() }
    }

    private fun GoogleSignInAccount.toSession(): GoogleAccountSession {
        return GoogleAccountSession(
            email = email.orEmpty(),
            displayName = displayName,
            photoUrl = photoUrl?.toString(),
            idToken = idToken
        )
    }
}
