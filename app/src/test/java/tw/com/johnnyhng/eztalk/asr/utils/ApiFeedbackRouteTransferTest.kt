package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiFeedbackRouteTransferTest {
    @Test
    fun decideFeedbackRouteChoosesTransferWhenMetadataIsMissing() {
        val result = decideFeedbackRoute(
            metadata = null,
            recognitionUrl = "https://example.com/api/process_audio"
        )

        assertEquals(FeedbackRoute.POST_TRANSFER, result)
    }

    @Test
    fun decideFeedbackRouteChoosesTransferWhenNoCandidateArraysExist() {
        val metadata = JSONObject().apply {
            put("modified", "current-text")
        }

        val result = decideFeedbackRoute(
            metadata = metadata,
            recognitionUrl = "https://example.com/api/process_audio"
        )

        assertEquals(FeedbackRoute.POST_TRANSFER, result)
    }
}
