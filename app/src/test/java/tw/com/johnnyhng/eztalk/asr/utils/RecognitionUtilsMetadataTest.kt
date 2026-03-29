package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionUtilsMetadataTest {
    @Test
    fun buildRemoteCandidateMetadataPreservesLatestEditedFieldsAndLocalCandidates() {
        val latestJson = JSONObject().apply {
            put("original", "latest-original")
            put("modified", "latest-modified")
            put("checked", true)
            put("mutable", false)
            put("removable", true)
            put("local_candidates", JSONArray(listOf("local-1", "local-2")))
        }

        val result = buildRemoteCandidateMetadata(
            latestJsonlData = latestJson,
            fallbackOriginalText = "fallback-original",
            fallbackCurrentText = "fallback-current",
            remoteCandidates = listOf("remote-1", "remote-2")
        )

        assertEquals("latest-original", result.originalText)
        assertEquals("latest-modified", result.modifiedText)
        assertTrue(result.checked)
        assertFalse(result.mutable)
        assertTrue(result.removable)
        assertEquals(listOf("local-1", "local-2"), result.localCandidates)
        assertEquals(listOf("remote-1", "remote-2"), result.remoteCandidates)
    }

    @Test
    fun buildRemoteCandidateMetadataFallsBackWhenLatestJsonIsMissing() {
        val result = buildRemoteCandidateMetadata(
            latestJsonlData = null,
            fallbackOriginalText = "fallback-original",
            fallbackCurrentText = "fallback-current",
            remoteCandidates = listOf("remote-1")
        )

        assertEquals("fallback-original", result.originalText)
        assertEquals("fallback-current", result.modifiedText)
        assertFalse(result.checked)
        assertTrue(result.mutable)
        assertFalse(result.removable)
        assertTrue(result.localCandidates.isEmpty())
        assertEquals(listOf("remote-1"), result.remoteCandidates)
    }

    @Test
    fun buildRemoteCandidateMetadataRespectsLegacyCanCheckWhenMutableIsMissing() {
        val latestJson = JSONObject().apply {
            put("original", "latest-original")
            put("modified", "latest-modified")
            put("checked", false)
            put("canCheck", false)
        }

        val result = buildRemoteCandidateMetadata(
            latestJsonlData = latestJson,
            fallbackOriginalText = "fallback-original",
            fallbackCurrentText = "fallback-current",
            remoteCandidates = listOf("remote-1")
        )

        assertFalse(result.mutable)
        assertEquals("latest-original", result.originalText)
        assertEquals("latest-modified", result.modifiedText)
    }
}
