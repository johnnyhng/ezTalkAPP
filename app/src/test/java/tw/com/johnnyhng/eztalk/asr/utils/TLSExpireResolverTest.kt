package tw.com.johnnyhng.eztalk.asr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException

class TLSExpireResolverTest {

    @Test
    fun resolveMessageReturnsExpiredMessageForExpiredCertificate() {
        val error = SSLHandshakeException("Chain validation failed").apply {
            initCause(CertificateExpiredException("Certificate expired"))
        }

        assertEquals(
            "TLS certificate expired",
            TLSExpireResolver.resolveMessage(error, "fallback")
        )
        assertTrue(TLSExpireResolver.isCertificateExpired(error))
    }

    @Test
    fun resolveMessageReturnsHandshakeMessageForHandshakeFailure() {
        val error = SSLHandshakeException("Handshake failed")

        assertEquals(
            "TLS handshake failed",
            TLSExpireResolver.resolveMessage(error, "fallback")
        )
        assertFalse(TLSExpireResolver.isCertificateExpired(error))
    }

    @Test
    fun resolveMessageFallsBackToOriginalMessage() {
        val error = IllegalStateException("backend unavailable")

        assertEquals(
            "backend unavailable",
            TLSExpireResolver.resolveMessage(error, "fallback")
        )
    }
}
