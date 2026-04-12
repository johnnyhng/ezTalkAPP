package tw.com.johnnyhng.eztalk.asr.utils

import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException

internal object TLSExpireResolver {
    fun resolveMessage(
        error: Throwable,
        fallbackMessage: String
    ): String {
        val rootCause = rootCause(error)
        return when {
            rootCause is CertificateExpiredException ->
                "TLS certificate expired"
            error is SSLHandshakeException || rootCause is SSLHandshakeException ->
                "TLS handshake failed"
            else ->
                error.message ?: fallbackMessage
        }
    }

    fun isCertificateExpired(error: Throwable): Boolean {
        return rootCause(error) is CertificateExpiredException
    }

    private fun rootCause(error: Throwable): Throwable {
        var current: Throwable = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }
}
