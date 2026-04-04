package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiFeedbackRouteRemoteTest {
    @Test
    fun decideFeedbackRouteChoosesPutUpdatesWhenRemoteCandidatesExist() {
        val metadata = JSONObject().apply {
            put("remote_candidates", JSONArray(listOf("remote-1")))
            put("local_candidates", JSONArray(listOf("local-1")))
        }

        val result = decideFeedbackRoute(
            metadata = metadata,
            backendUrl = "https://example.com"
        )

        assertEquals(FeedbackRoute.PUT_UPDATES, result)
    }
}
