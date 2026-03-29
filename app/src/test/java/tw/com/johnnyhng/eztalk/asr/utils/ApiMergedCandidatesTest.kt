package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiMergedCandidatesTest {
    @Test
    fun buildMergedCandidatesKeepsLocalCandidatesBeforeRemoteCandidates() {
        val metadata = JSONObject().apply {
            put("local_candidates", JSONArray(listOf("local-1", "local-2")))
            put("remote_candidates", JSONArray(listOf("remote-1", "remote-2")))
        }

        val result = buildMergedCandidates(metadata)

        assertEquals(
            listOf("local-1", "local-2", "remote-1", "remote-2"),
            result.toStringList()
        )
    }

    @Test
    fun buildMergedCandidatesUsesDistinctWhilePreservingFirstSeenOrder() {
        val metadata = JSONObject().apply {
            put("local_candidates", JSONArray(listOf("shared", "local-only", "shared")))
            put("remote_candidates", JSONArray(listOf("shared", "remote-only", "local-only")))
        }

        val result = buildMergedCandidates(metadata)

        assertEquals(
            listOf("shared", "local-only", "remote-only"),
            result.toStringList()
        )
    }

    @Test
    fun buildMergedCandidatesReturnsEmptyArrayWhenMetadataIsMissing() {
        val result = buildMergedCandidates(null)

        assertTrue(result.length() == 0)
    }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { index -> getString(index) }
}
