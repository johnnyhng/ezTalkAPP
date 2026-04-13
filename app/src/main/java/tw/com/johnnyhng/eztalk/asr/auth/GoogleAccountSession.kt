package tw.com.johnnyhng.eztalk.asr.auth

internal data class GoogleAccountSession(
    val email: String,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val idToken: String? = null
)

internal fun GoogleAccountSession.displayLabel(): String {
    val resolvedName = displayName?.trim().orEmpty()
    return if (resolvedName.isNotBlank() && resolvedName != email) {
        "$resolvedName ($email)"
    } else {
        email
    }
}
