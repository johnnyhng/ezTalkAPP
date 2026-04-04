package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL

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
    fun buildUploadJsonMetadataUsesDefaultRemoteCandidatesWhenOmitted() {
        val metadata = buildUploadJsonMetadata(
            filename = "sample.wav",
            userId = "tester@example.com",
            label = "confirmed text"
        )

        assertEquals(0, metadata.getJSONArray("remote_candidates").length())
    }

    @Test
    fun parseUploadMetadataSnapshotExtractsModifiedTextAndRemoteCandidates() {
        val snapshot = parseUploadMetadataSnapshot(
            """
            {
              "modified": "confirmed text",
              "remote_candidates": ["r1", "r2"]
            }
            """.trimIndent()
        )

        assertEquals("confirmed text", snapshot.label)
        assertEquals(2, snapshot.remoteCandidates.length())
        assertEquals("r1", snapshot.remoteCandidates.getString(0))
        assertEquals("r2", snapshot.remoteCandidates.getString(1))
    }

    @Test
    fun buildPackagedUploadJsonReturnsNullWhenEitherInputIsMissing() {
        assertNull(buildPackagedUploadJson(null, JSONArray()))
        assertNull(buildPackagedUploadJson(JSONObject(), null))
    }

    @Test
    fun buildPackagedUploadJsonPreservesMetadataAndRawArray() {
        val metadata = JSONObject().apply {
            put("label", "confirmed")
        }
        val raw = JSONArray().apply {
            put(3)
            put(4)
        }

        val packaged = buildPackagedUploadJson(metadata, raw)

        assertNotNull(packaged)
        assertEquals("confirmed", packaged?.metadata?.getString("label"))
        assertEquals(2, packaged?.raw?.length())
    }

    @Test
    fun buildMultipartRequestContentBuildsMultipartHeadersAndPayload() {
        val file = File(TestFixtures.tempDir("multipart-content"), "sample.wav").apply {
            writeBytes(ByteArray(48) { index -> index.toByte() })
        }
        val content = buildMultipartRequestContent(
            jsonPayload = JSONObject().apply { put("label", "confirmed") },
            file = file,
            boundary = "Boundary-test"
        )
        val body = String(content.body, Charsets.UTF_8)

        assertEquals("multipart/form-data; boundary=Boundary-test", content.contentType)
        assertTrue(body.contains("name=\"json\""))
        assertTrue(body.contains("\"label\":\"confirmed\""))
        assertTrue(body.contains("filename=\"sample.wav\""))
        assertTrue(body.contains("Content-Type: audio/wav"))
        assertTrue(body.contains("--Boundary-test--"))
    }

    @Test
    fun buildJsonRequestContentSerializesPayloadAsUtf8Json() {
        val bytes = buildJsonRequestContent(JSONObject().apply {
            put("label", "confirmed")
            put("value", 3)
        })

        assertEquals("""{"label":"confirmed","value":3}""", String(bytes, Charsets.UTF_8))
    }

    @Test
    fun writeUploadRequestWritesJsonPayloadAndHeaders() {
        val connection = FakeHttpURLConnection()
        val payload = JSONObject().apply { put("label", "confirmed") }

        writeUploadRequest(connection, UploadRequestPlan.Json(payload))

        assertEquals("application/json; charset=utf-8", connection.properties["Content-Type"])
        assertEquals("application/json", connection.properties["Accept"])
        assertEquals("""{"label":"confirmed"}""", connection.outputAsString())
    }

    @Test
    fun writeUploadRequestWritesMultipartPayloadAndBoundary() {
        val file = File(TestFixtures.tempDir("multipart-write"), "sample.wav").apply {
            writeBytes(ByteArray(48) { index -> index.toByte() })
        }
        val connection = FakeHttpURLConnection()

        writeUploadRequest(
            connection = connection,
            plan = UploadRequestPlan.Multipart(
                file = file,
                payload = JSONObject().apply { put("label", "confirmed") }
            ),
            boundaryProvider = { "Boundary-fixed" }
        )

        assertEquals("multipart/form-data; boundary=Boundary-fixed", connection.properties["Content-Type"])
        val body = connection.outputAsString()
        assertTrue(body.contains("name=\"json\""))
        assertTrue(body.contains("\"label\":\"confirmed\""))
        assertTrue(body.contains("filename=\"sample.wav\""))
    }

    @Test
    fun readStreamTextReturnsNullForMissingStream() {
        assertNull(readStreamText(null))
    }

    @Test
    fun readStreamTextReturnsWholeStreamContent() {
        val text = readStreamText(ByteArrayInputStream("error body".toByteArray()))

        assertEquals("error body", text)
    }

    @Test
    fun parseRecognitionResponseBodyReturnsNestedResponseObject() {
        val response = parseRecognitionResponseBody(
            """
            {
              "response": {
                "result": "ok",
                "sentence_candidates": ["a", "b"]
              }
            }
            """.trimIndent()
        )

        assertNotNull(response)
        assertEquals("ok", response?.getString("result"))
        assertEquals("b", response?.getJSONArray("sentence_candidates")?.getString(1))
    }

    @Test
    fun parseRecognitionResponseBodyReturnsNullWhenResponseFieldIsMissingOrNotObject() {
        assertNull(parseRecognitionResponseBody("""{"foo":1}"""))
        assertNull(parseRecognitionResponseBody("""{"response":"bad"}"""))
    }

    @Test
    fun buildFeedbackDispatchPlanUsesUpdatesEndpointWhenRemoteCandidatesExist() {
        val metadata = JSONObject().apply {
            put("remote_candidates", JSONArray().apply { put("r1") })
        }

        val plan = buildFeedbackDispatchPlan(
            backendUrl = "https://backend.example.com",
            metadata = metadata
        )

        assertEquals(FeedbackRoute.PUT_UPDATES, plan.route)
        assertEquals("https://backend.example.com/updates", plan.endpoint)
    }

    @Test
    fun buildFeedbackDispatchPlanUsesRecognitionEndpointForLocalOnlyCandidates() {
        val metadata = JSONObject().apply {
            put("local_candidates", JSONArray().apply { put("l1") })
        }

        val plan = buildFeedbackDispatchPlan(
            backendUrl = "https://backend.example.com",
            metadata = metadata
        )

        assertEquals(FeedbackRoute.POST_PROCESS_AUDIO, plan.route)
        assertEquals("https://backend.example.com/process_audio", plan.endpoint)
    }

    @Test
    fun buildFeedbackDispatchPlanFallsBackToTransferEndpoint() {
        val plan = buildFeedbackDispatchPlan(
            backendUrl = "https://backend.example.com",
            metadata = null
        )

        assertEquals(FeedbackRoute.POST_TRANSFER, plan.route)
        assertEquals("https://backend.example.com/transfer", plan.endpoint)
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

    private class FakeHttpURLConnection : HttpURLConnection(URL("http://localhost")) {
        val properties = linkedMapOf<String, String>()
        private val output = java.io.ByteArrayOutputStream()

        override fun setRequestProperty(key: String?, value: String?) {
            if (key != null && value != null) {
                properties[key] = value
            }
        }

        override fun getOutputStream(): OutputStream = output

        fun outputAsString(): String = output.toString(Charsets.UTF_8.name())

        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
        override fun setRequestMethod(method: String?) {
            this.method = method
        }
        override fun getInputStream() = throw UnsupportedOperationException()
        override fun getResponseCode(): Int = 200
        override fun getResponseMessage(): String = "OK"
    }
}
