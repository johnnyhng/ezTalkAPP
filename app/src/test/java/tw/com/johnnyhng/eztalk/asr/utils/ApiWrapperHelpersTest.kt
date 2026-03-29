package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiWrapperHelpersTest {
    @Test
    fun wavBytesToJsonArrayReturnsNullWhenHeaderIsTooShort() {
        val result = wavBytesToJsonArray(ByteArray(10))

        assertNull(result)
    }

    @Test
    fun wavBytesToJsonArrayConvertsSignedBytesToUnsignedIntegers() {
        val bytes = ByteArray(46).also {
            it[0] = 0
            it[1] = 1
            it[44] = (-1).toByte()
            it[45] = 127
        }

        val result = wavBytesToJsonArray(bytes)

        assertNotNull(result)
        assertEquals(46, result?.length())
        assertEquals(255, result?.getInt(44))
        assertEquals(127, result?.getInt(45))
    }

    @Test
    fun buildUploadJsonMetadataBuildsExpectedShape() {
        val metadata = buildUploadJsonMetadata(
            filename = "sample.wav",
            userId = "tester@example.com",
            label = "confirmed text",
            remoteCandidates = JSONArray().apply {
                put("r1")
                put("r2")
            }
        )

        assertEquals("tester", metadata.getJSONObject("account").getString("user_id"))
        assertEquals("confirmed text", metadata.getString("label"))
        assertEquals("confirmed text", metadata.getString("sentence"))
        assertEquals("sample.wav", metadata.getString("filename"))
        assertEquals(false, metadata.getBoolean("charMode"))
        assertEquals(2, metadata.getJSONArray("remote_candidates").length())
    }

    @Test
    fun buildFeedbackDispatchPlanUsesUpdatesEndpointWhenRemoteCandidatesExist() {
        val metadata = JSONObject().apply {
            put("remote_candidates", JSONArray().apply { put("r1") })
        }

        val plan = buildFeedbackDispatchPlan(
            backendUrl = "https://backend.example.com",
            recognitionUrl = "https://recognition.example.com/process_audio",
            metadata = metadata
        )

        assertEquals(FeedbackRoute.PUT_UPDATES, plan.route)
        assertEquals("https://backend.example.com/api/updates", plan.endpoint)
    }

    @Test
    fun buildFeedbackDispatchPlanUsesRecognitionEndpointForLocalOnlyCandidates() {
        val metadata = JSONObject().apply {
            put("local_candidates", JSONArray().apply { put("l1") })
        }

        val plan = buildFeedbackDispatchPlan(
            backendUrl = "https://backend.example.com",
            recognitionUrl = "https://recognition.example.com/process_audio",
            metadata = metadata
        )

        assertEquals(FeedbackRoute.POST_PROCESS_AUDIO, plan.route)
        assertEquals("https://recognition.example.com/process_audio", plan.endpoint)
    }

    @Test
    fun buildFeedbackDispatchPlanFallsBackToTransferEndpoint() {
        val plan = buildFeedbackDispatchPlan(
            backendUrl = "https://backend.example.com",
            recognitionUrl = "",
            metadata = null
        )

        assertEquals(FeedbackRoute.POST_TRANSFER, plan.route)
        assertEquals("https://backend.example.com/api/transfer", plan.endpoint)
    }

    @Test
    fun combineUploadJsonReturnsNullWhenEitherInputIsMissing() {
        assertNull(combineUploadJson(null, JSONArray()))
        assertNull(combineUploadJson(JSONObject(), null))
    }

    @Test
    fun combineUploadJsonCopiesMetadataAndAddsRawArray() {
        val metadata = JSONObject().apply {
            put("label", "confirmed")
        }
        val raw = JSONArray().apply {
            put(1)
            put(2)
        }

        val combined = combineUploadJson(metadata, raw)

        assertNotNull(combined)
        assertEquals("confirmed", combined?.getString("label"))
        assertEquals(2, combined?.getJSONArray("raw")?.length())
        assertTrue(!metadata.has("raw"))
    }

    @Test
    fun executeFeedbackDispatchInvokesPutUpdatesForRemoteRoute() {
        var called = ""

        val result = executeFeedbackDispatch(
            dispatchPlan = FeedbackDispatchPlan(FeedbackRoute.PUT_UPDATES, "https://backend/api/updates"),
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadata = JSONObject(),
            putUpdates = { endpoint, _, _, _ ->
                called = endpoint
                true
            },
            postProcessAudio = { _, _, _, _ -> false },
            postTransfer = { _, _, _ -> false }
        )

        assertTrue(result)
        assertEquals("https://backend/api/updates", called)
    }

    @Test
    fun executeFeedbackDispatchInvokesProcessAudioForLocalRoute() {
        var called = ""

        val result = executeFeedbackDispatch(
            dispatchPlan = FeedbackDispatchPlan(FeedbackRoute.POST_PROCESS_AUDIO, "https://recognition/process_audio"),
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadata = JSONObject(),
            putUpdates = { _, _, _, _ -> false },
            postProcessAudio = { endpoint, _, _, _ ->
                called = endpoint
                true
            },
            postTransfer = { _, _, _ -> false }
        )

        assertTrue(result)
        assertEquals("https://recognition/process_audio", called)
    }

    @Test
    fun executeFeedbackDispatchInvokesTransferForFallbackRoute() {
        var called = ""

        val result = executeFeedbackDispatch(
            dispatchPlan = FeedbackDispatchPlan(FeedbackRoute.POST_TRANSFER, "https://backend/api/transfer"),
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadata = null,
            putUpdates = { _, _, _, _ -> false },
            postProcessAudio = { _, _, _, _ -> false },
            postTransfer = { endpoint, _, _ ->
                called = endpoint
                true
            }
        )

        assertTrue(result)
        assertEquals("https://backend/api/transfer", called)
    }
}
