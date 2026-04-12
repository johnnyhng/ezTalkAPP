package tw.com.johnnyhng.eztalk.asr.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.tasks.await
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import tw.com.johnnyhng.eztalk.asr.TAG

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
        if (idToken.isNullOrBlank()) {
            Log.w(TAG, "Firebase sign in skipped because Google session has no ID token for email=${session.email}")
            return Result.failure(IllegalStateException("Google ID token unavailable"))
        }
        Log.i(TAG, "Firebase sign in requested for email=${session.email} hasIdToken=${idToken.isNotBlank()}")
        return runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
            Log.i(
                TAG,
                "Firebase sign in succeeded email=${session.email} uid=${authResult.user?.uid.orEmpty()}"
            )
            Unit
        }.onFailure { error ->
            Log.w(TAG, "Firebase sign in failed for email=${session.email}", error)
        }
    }

    suspend fun restoreFirebaseSession(context: Context): Result<Unit> {
        val session = getCurrentSession(context)
        if (session == null) {
            Log.w(TAG, "Firebase session restore skipped because there is no Google session")
            return Result.failure(IllegalStateException("No Google session available"))
        }
        Log.i(TAG, "Attempting Firebase session restore for email=${session.email} hasIdToken=${!session.idToken.isNullOrBlank()}")
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
