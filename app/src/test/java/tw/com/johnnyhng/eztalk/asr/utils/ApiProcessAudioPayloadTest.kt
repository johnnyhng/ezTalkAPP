package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiProcessAudioPayloadTest {
    @Test
    fun buildProcessAudioPayloadMatchesExpectedShapeWithMetadataAndRaw() {
        val metadata = JSONObject().apply {
            put("modified", "confirmed text")
        }
        val raw = JSONArray(listOf(1, 2, 3))

        val payload = buildProcessAudioPayload(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadata = metadata,
            raw = raw
        )

        assertEquals("tester", payload.getString("login_user"))
        assertEquals("sample.wav", payload.getString("filename"))
        assertEquals("confirmed text", payload.getString("label"))
        assertEquals(8, payload.getInt("num_of_stn"))
        assertEquals(listOf(1, 2, 3), payload.getJSONArray("raw").toIntList())
    }

    @Test
    fun buildProcessAudioPayloadFallsBackToTmpWhenMetadataIsMissing() {
        val payload = buildProcessAudioPayload(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com"
        )

        assertEquals("tester", payload.getString("login_user"))
        assertEquals("sample.wav", payload.getString("filename"))
        assertEquals("tmp", payload.getString("label"))
        assertEquals(8, payload.getInt("num_of_stn"))
        assertFalse(payload.has("raw"))
    }

    @Test
    fun buildProcessAudioPayloadOmitsRawWhenNotProvided() {
        val metadata = JSONObject().apply {
            put("modified", "confirmed text")
        }

        val payload = buildProcessAudioPayload(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadata = metadata
        )

        assertTrue(payload.has("login_user"))
        assertTrue(payload.has("filename"))
        assertTrue(payload.has("label"))
        assertTrue(payload.has("num_of_stn"))
        assertFalse(payload.has("raw"))
    }

    private fun JSONArray.toIntList(): List<Int> =
        List(length()) { index -> getInt(index) }
}
