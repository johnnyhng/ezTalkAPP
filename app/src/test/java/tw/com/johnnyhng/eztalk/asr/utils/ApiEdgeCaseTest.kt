package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiEdgeCaseTest {
    @Test
    fun decideFeedbackRouteStillChoosesPutUpdatesWhenRemoteCandidatesExistAndRecognitionUrlIsBlank() {
        val metadata = JSONObject().apply {
            put("remote_candidates", JSONArray(listOf("remote-1")))
        }

        val result = decideFeedbackRoute(
            metadata = metadata,
            recognitionUrl = ""
        )

        assertEquals(FeedbackRoute.PUT_UPDATES, result)
    }

    @Test
    fun buildProcessAudioPayloadUsesEmptyStringWhenMetadataExistsWithoutModifiedField() {
        val payload = buildProcessAudioPayload(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadata = JSONObject()
        )

        assertEquals("", payload.getString("label"))
    }

    @Test
    fun buildMergedCandidatesReturnsEmptyArrayWhenMetadataHasNoCandidateArrays() {
        val result = buildMergedCandidates(JSONObject().apply {
            put("modified", "text")
        })

        assertEquals(0, result.length())
    }
}
