package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionUtilsCacheTest {
    @Test
    fun readCachedRemoteCandidatesReturnsEmptyWhenMetadataIsMissing() {
        val result = readCachedRemoteCandidates(null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun readCachedRemoteCandidatesReturnsEmptyWhenRemoteCandidatesKeyIsMissing() {
        val json = JSONObject().apply {
            put("modified", "current-text")
        }

        val result = readCachedRemoteCandidates(json)

        assertTrue(result.isEmpty())
    }

    @Test
    fun readCachedRemoteCandidatesReturnsCachedCandidatesInOrder() {
        val json = JSONObject().apply {
            put("remote_candidates", JSONArray(listOf("remote-1", "remote-2", "remote-3")))
        }

        val result = readCachedRemoteCandidates(json)

        assertEquals(listOf("remote-1", "remote-2", "remote-3"), result)
    }
}
