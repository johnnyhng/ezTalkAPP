package tw.com.johnnyhng.eztalk.asr.auth

internal data class GoogleAccountSession(
    val email: String,
    val displayName: String? = null
)
