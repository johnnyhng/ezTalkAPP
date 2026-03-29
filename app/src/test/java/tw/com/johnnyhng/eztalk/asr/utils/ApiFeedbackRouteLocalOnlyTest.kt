package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiFeedbackRouteLocalOnlyTest {
    @Test
    fun decideFeedbackRouteChoosesProcessAudioWhenOnlyLocalCandidatesExist() {
        val metadata = JSONObject().apply {
            put("local_candidates", JSONArray(listOf("local-1")))
        }

        val result = decideFeedbackRoute(
            metadata = metadata,
            recognitionUrl = "https://example.com/api/process_audio"
        )

        assertEquals(FeedbackRoute.POST_PROCESS_AUDIO, result)
    }

    @Test
    fun decideFeedbackRouteFallsBackToTransferWhenRecognitionUrlIsBlank() {
        val metadata = JSONObject().apply {
            put("local_candidates", JSONArray(listOf("local-1")))
        }

        val result = decideFeedbackRoute(
            metadata = metadata,
            recognitionUrl = ""
        )

        assertEquals(FeedbackRoute.POST_TRANSFER, result)
    }
}
