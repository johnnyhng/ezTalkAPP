package tw.com.johnnyhng.eztalk.asr.utils

import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException

internal object TLSExpireResolver {
    fun resolveMessage(
        error: Throwable,
        fallbackMessage: String
    ): String {
        val causeChain = causeChain(error)
        return when {
            causeChain.any { it is CertificateExpiredException } ->
                "Secure connection failed: server TLS certificate expired"
            causeChain.any { it is SSLHandshakeException } ->
                "Secure connection failed during TLS handshake"
            else ->
                error.message ?: fallbackMessage
        }
    }

    fun isCertificateExpired(error: Throwable): Boolean {
        return causeChain(error).any { it is CertificateExpiredException }
    }

    private fun causeChain(error: Throwable): List<Throwable> {
        val causes = mutableListOf<Throwable>()
        var current: Throwable? = error
        while (current != null && causes.none { it === current }) {
            causes += current
            current = current.cause
        }
        return causes
    }
}
