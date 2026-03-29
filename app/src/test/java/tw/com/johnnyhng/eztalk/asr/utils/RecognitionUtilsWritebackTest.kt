package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionUtilsWritebackTest {
    @Test
    fun buildRemoteCandidateWritebackUsesLatestMetadataInsteadOfFallbackValues() {
        val latestJson = JSONObject().apply {
            put("original", "latest-original")
            put("modified", "latest-modified")
            put("checked", true)
            put("mutable", false)
            put("removable", true)
            put("local_candidates", JSONArray(listOf("local-1")))
        }
        val response = JSONObject().apply {
            put("sentence_candidates", JSONArray(listOf("remote-1", "remote-2")))
        }

        val result = buildRemoteCandidateWriteback(
            latestJsonlData = latestJson,
            fallbackOriginalText = "fallback-original",
            fallbackCurrentText = "fallback-current",
            response = response
        )

        assertNotNull(result)
        assertEquals("latest-original", result?.originalText)
        assertEquals("latest-modified", result?.modifiedText)
        assertTrue(result?.checked == true)
        assertFalse(result?.mutable == true)
        assertTrue(result?.removable == true)
        assertEquals(listOf("local-1"), result?.localCandidates)
        assertEquals(listOf("remote-1", "remote-2"), result?.remoteCandidates)
    }

    @Test
    fun buildRemoteCandidateWritebackFallsBackWhenLatestMetadataIsMissing() {
        val response = JSONObject().apply {
            put("sentence_candidates", JSONArray(listOf("remote-1")))
        }

        val result = buildRemoteCandidateWriteback(
            latestJsonlData = null,
            fallbackOriginalText = "fallback-original",
            fallbackCurrentText = "fallback-current",
            response = response
        )

        assertNotNull(result)
        assertEquals("fallback-original", result?.originalText)
        assertEquals("fallback-current", result?.modifiedText)
        assertFalse(result?.checked == true)
        assertTrue(result?.mutable == true)
        assertFalse(result?.removable == true)
        assertTrue(result?.localCandidates?.isEmpty() == true)
        assertEquals(listOf("remote-1"), result?.remoteCandidates)
    }

    @Test
    fun buildRemoteCandidateWritebackReturnsNullWhenResponseCannotProduceCandidates() {
        val response = JSONObject().apply {
            put("unexpected_key", "value")
        }

        val result = buildRemoteCandidateWriteback(
            latestJsonlData = JSONObject(),
            fallbackOriginalText = "fallback-original",
            fallbackCurrentText = "fallback-current",
            response = response
        )

        assertNull(result)
    }
}
