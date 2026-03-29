package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionUtilsParsingTest {
    @Test
    fun parseRemoteCandidatesReturnsSentenceCandidatesInOrder() {
        val response = JSONObject().apply {
            put("sentence_candidates", JSONArray(listOf("cand-1", "cand-2", "cand-3")))
        }

        val result = parseRemoteCandidates(response)

        assertEquals(listOf("cand-1", "cand-2", "cand-3"), result)
    }

    @Test
    fun parseRemoteCandidatesFiltersBlankValues() {
        val response = JSONObject().apply {
            put("sentence_candidates", JSONArray(listOf("cand-1", "", "   ", "cand-2")))
        }

        val result = parseRemoteCandidates(response)

        assertEquals(listOf("cand-1", "cand-2"), result)
    }

    @Test
    fun parseRemoteCandidatesReturnsEmptyWhenResponseIsNull() {
        val result = parseRemoteCandidates(null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun parseRemoteCandidatesReturnsEmptyWhenSentenceCandidatesIsMissing() {
        val response = JSONObject().apply {
            put("response", "ok")
        }

        val result = parseRemoteCandidates(response)

        assertTrue(result.isEmpty())
    }
}
