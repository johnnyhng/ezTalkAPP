package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiRequestPayloadTest {
    @Test
    fun buildRecognitionPayloadMatchesExpectedShapeWithRaw() {
        val raw = JSONArray(listOf(1, 2, 3))

        val payload = buildRecognitionPayload(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            raw = raw
        )

        assertEquals("tester", payload.getString("login_user"))
        assertEquals("sample.wav", payload.getString("filename"))
        assertEquals("tmp", payload.getString("label"))
        assertEquals(8, payload.getInt("num_of_stn"))
        assertEquals(listOf(1, 2, 3), payload.getJSONArray("raw").toIntList())
    }

    @Test
    fun buildRecognitionPayloadOmitsRawWhenNotProvided() {
        val payload = buildRecognitionPayload(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com"
        )

        assertFalse(payload.has("raw"))
    }

    @Test
    fun buildUpdatePayloadIncludesAccountSentenceAndMergedCandidates() {
        val metadata = JSONObject().apply {
            put("modified", "confirmed text")
            put("local_candidates", JSONArray(listOf("l1", "shared")))
            put("remote_candidates", JSONArray(listOf("shared", "r1")))
        }

        val payload = buildUpdatePayload(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadata = metadata
        )

        assertEquals("tester", payload.getJSONObject("account").getString("user_id"))
        assertTrue(payload.getJSONObject("account").has("password"))
        assertEquals("True", payload.getString("update_files"))
        assertEquals("confirmed text", payload.getString("sentence"))

        val moved = payload.getJSONArray("streamFilesMove").getJSONObject(0)
        val label = moved.getJSONObject("sample.wav")
        assertEquals("tmp", label.getString("original"))
        assertEquals("confirmed text", label.getString("modified"))
        assertEquals(listOf("l1", "shared", "r1"), label.getJSONArray("candidates").toStringList())
    }

    @Test
    fun buildUpdatePayloadFallsBackToEmptySentenceAndCandidatesWhenMetadataIsMissing() {
        val payload = buildUpdatePayload(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadata = null
        )

        assertEquals("", payload.getString("sentence"))
        val moved = payload.getJSONArray("streamFilesMove").getJSONObject(0)
        val label = moved.getJSONObject("sample.wav")
        assertEquals("", label.getString("modified"))
        assertEquals(0, label.getJSONArray("candidates").length())
    }

    @Test
    fun buildUpdatePayloadUsesDefaultNullMetadataWhenOmitted() {
        val payload = buildUpdatePayload(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com"
        )

        assertEquals("", payload.getString("sentence"))
        val moved = payload.getJSONArray("streamFilesMove").getJSONObject(0)
        val label = moved.getJSONObject("sample.wav")
        assertEquals("", label.getString("modified"))
    }

    private fun JSONArray.toIntList(): List<Int> =
        List(length()) { index -> getInt(index) }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { index -> getString(index) }
}
