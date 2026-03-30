package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiHttpIntegrationTest {
    @Test
    fun postProcessAudioJsonModePostsExpectedPayload() {
        val wavFile = createTempWavFile("process-json")
        val response = withHttpServer(responseCode = 200, responseBody = """{"ok":true}""") { serverUrl, requestRef ->
            val success = postProcessAudio(
                processAudioUrl = "$serverUrl/process_audio",
                filePath = wavFile.absolutePath,
                userId = "tester@example.com",
                metadata = JSONObject().apply { put("modified", "confirmed text") },
                sendFileByJson = true
            )
            assertTrue(success)
            requestRef.get()
        }

        assertEquals("POST", response.method)
        assertTrue(response.contentType.contains("application/json"))
        val body = JSONObject(response.body)
        assertEquals("tester", body.getString("login_user"))
        assertEquals("confirmed text", body.getString("label"))
        assertEquals(wavFile.name, body.getString("filename"))
        assertTrue(body.getJSONArray("raw").length() >= 44)
    }

    @Test
    fun postProcessAudioMultipartModeUploadsJsonAndFileParts() {
        val wavFile = createTempWavFile("process-multipart")
        val response = withHttpServer(responseCode = 200, responseBody = """{"ok":true}""") { serverUrl, requestRef ->
            val success = postProcessAudio(
                processAudioUrl = "$serverUrl/process_audio",
                filePath = wavFile.absolutePath,
                userId = "tester@example.com",
                metadata = JSONObject().apply { put("modified", "confirmed text") },
                sendFileByJson = false
            )
            assertTrue(success)
            requestRef.get()
        }

        assertEquals("POST", response.method)
        assertTrue(response.contentType.contains("multipart/form-data"))
        assertTrue(response.body.contains("name=\"json\""))
        assertTrue(response.body.contains("\"label\":\"confirmed text\""))
        assertTrue(response.body.contains("filename=\"${wavFile.name}\""))
    }

    @Test
    fun putForUpdatesSendsMergedCandidatesPayload() {
        val response = withHttpServer(responseCode = 200, responseBody = """{"ok":true}""") { serverUrl, requestRef ->
            val success = putForUpdates(
                updateUrl = "$serverUrl/api/updates",
                filePath = "/tmp/sample.wav",
                userId = "tester@example.com",
                metadata = JSONObject().apply {
                    put("modified", "confirmed text")
                    put("local_candidates", JSONArray(listOf("l1", "shared")))
                    put("remote_candidates", JSONArray(listOf("shared", "r1")))
                }
            )
            assertTrue(success)
            requestRef.get()
        }

        assertEquals("PUT", response.method)
        val body = JSONObject(response.body)
        val label = body.getJSONArray("streamFilesMove")
            .getJSONObject(0)
            .getJSONObject("sample.wav")
        assertEquals("confirmed text", label.getString("modified"))
        assertEquals(
            listOf("l1", "shared", "r1"),
            List(label.getJSONArray("candidates").length()) { index ->
                label.getJSONArray("candidates").getString(index)
            }
        )
    }

    @Test
    fun postTransferJsonModeSendsPackagedUploadJson() {
        val wavFile = createTempWavFile("transfer-json")
        File(wavFile.parentFile, wavFile.nameWithoutExtension + ".jsonl").writeText(
            """
            {
              "modified": "confirmed text",
              "remote_candidates": ["r1", "r2"]
            }
            """.trimIndent()
        )

        val response = withHttpServer(responseCode = 200, responseBody = """{"ok":true}""") { serverUrl, requestRef ->
            val success = postTransfer(
                transferUrl = "$serverUrl/api/transfer",
                filePath = wavFile.absolutePath,
                userId = "tester@example.com",
                sendFileByJson = true
            )
            assertTrue(success)
            requestRef.get()
        }

        assertEquals("POST", response.method)
        assertTrue(response.contentType.contains("application/json"))
        val body = JSONObject(response.body)
        assertEquals("confirmed text", body.getString("label"))
        assertEquals("sample.wav", body.getString("filename"))
        assertEquals(2, body.getJSONArray("remote_candidates").length())
        assertTrue(body.getJSONArray("raw").length() >= 44)
    }

    @Test
    fun postTransferMultipartModeUploadsJsonAndFileParts() {
        val wavFile = createTempWavFile("transfer-multipart")
        File(wavFile.parentFile, wavFile.nameWithoutExtension + ".jsonl").writeText(
            """
            {
              "modified": "confirmed text",
              "remote_candidates": ["r1"]
            }
            """.trimIndent()
        )

        val response = withHttpServer(responseCode = 200, responseBody = """{"ok":true}""") { serverUrl, requestRef ->
            val success = postTransfer(
                transferUrl = "$serverUrl/api/transfer",
                filePath = wavFile.absolutePath,
                userId = "tester@example.com",
                sendFileByJson = false
            )
            assertTrue(success)
            requestRef.get()
        }

        assertEquals("POST", response.method)
        assertTrue(response.contentType.contains("multipart/form-data"))
        assertTrue(response.body.contains("name=\"json\""))
        assertTrue(response.body.contains("\"label\":\"confirmed text\""))
        assertTrue(response.body.contains("filename=\"${wavFile.name}\""))
    }

    @Test
    fun postForRecognitionJsonModeParsesResponseObject() {
        val wavFile = createTempWavFile("recognition-json")

        val response = withHttpServer(
            responseCode = 200,
            responseBody = """
                {
                  "response": {
                    "result": "ok",
                    "sentence_candidates": ["a", "b"]
                  }
                }
            """.trimIndent()
        ) { serverUrl, requestRef ->
            val result = postForRecognition(
                recognitionUrl = "$serverUrl/process_audio",
                filePath = wavFile.absolutePath,
                userId = "tester@example.com",
                sendFileByJson = true
            )
            assertEquals("POST", requestRef.get().method)
            result
        }

        assertNotNull(response)
        assertEquals("ok", response?.getString("result"))
        assertEquals("a", response?.getJSONArray("sentence_candidates")?.getString(0))
    }

    @Test
    fun postForRecognitionMultipartModeParsesResponseObject() {
        val wavFile = createTempWavFile("recognition-multipart")

        val response = withHttpServer(
            responseCode = 200,
            responseBody = """
                {
                  "response": {
                    "result": "multipart-ok",
                    "sentence_candidates": ["x", "y"]
                  }
                }
            """.trimIndent()
        ) { serverUrl, requestRef ->
            val result = postForRecognition(
                recognitionUrl = "$serverUrl/process_audio",
                filePath = wavFile.absolutePath,
                userId = "tester@example.com",
                sendFileByJson = false
            )
            val request = requestRef.get()
            assertEquals("POST", request.method)
            assertTrue(request.contentType.contains("multipart/form-data"))
            assertTrue(request.body.contains("filename=\"${wavFile.name}\""))
            result
        }

        assertNotNull(response)
        assertEquals("multipart-ok", response?.getString("result"))
        assertEquals("x", response?.getJSONArray("sentence_candidates")?.getString(0))
    }

    @Test
    fun postProcessAudioReturnsFalseOnNonSuccessResponse() {
        val wavFile = createTempWavFile("process-failure")

        val success = withHttpServer(
            responseCode = 500,
            responseBody = """{"error":"failed"}"""
        ) { serverUrl, _ ->
            postProcessAudio(
                processAudioUrl = "$serverUrl/process_audio",
                filePath = wavFile.absolutePath,
                userId = "tester@example.com",
                metadata = JSONObject().apply { put("modified", "confirmed text") },
                sendFileByJson = true
            )
        }

        assertFalse(success)
    }

    @Test
    fun postForRecognitionReturnsNullOnNonSuccessResponse() {
        val wavFile = createTempWavFile("recognition-failure")

        val result = withHttpServer(
            responseCode = 500,
            responseBody = """{"error":"failed"}"""
        ) { serverUrl, _ ->
            postForRecognition(
                recognitionUrl = "$serverUrl/process_audio",
                filePath = wavFile.absolutePath,
                userId = "tester@example.com",
                sendFileByJson = true
            )
        }

        assertEquals(null, result)
    }

    private fun createTempWavFile(prefix: String): File =
        File(TestFixtures.tempDir(prefix), "sample.wav").apply {
            writeBytes(ByteArray(48) { index -> index.toByte() })
        }

    private fun <T> withHttpServer(
        responseCode: Int,
        responseBody: String,
        block: (serverUrl: String, requestRef: AtomicReference<RecordedRequest>) -> T
    ): T {
        ServerSocket(0).use { serverSocket ->
            val requestRef = AtomicReference<RecordedRequest>()
            val latch = CountDownLatch(1)
            val acceptThread = Thread {
                serverSocket.accept().use { socket ->
                    requestRef.set(readRequest(socket))
                    writeResponse(socket, responseCode, responseBody)
                    latch.countDown()
                }
            }
            acceptThread.start()
            try {
                val result = block("http://127.0.0.1:${serverSocket.localPort}", requestRef)
                assertTrue("HTTP request was not received in time", latch.await(5, TimeUnit.SECONDS))
                return result
            } finally {
                if (!serverSocket.isClosed) {
                    serverSocket.close()
                }
                acceptThread.join(1000)
            }
        }
    }

    private fun readRequest(socket: Socket): RecordedRequest {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        val requestLine = reader.readLine()
        val headers = linkedMapOf<String, String>()
        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                val name = line.substring(0, separator)
                val value = line.substring(separator + 1).trim()
                headers[name] = value
                if (name.equals("Content-Length", ignoreCase = true)) {
                    contentLength = value.toInt()
                }
            }
        }

        val bodyChars = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = reader.read(bodyChars, read, contentLength - read)
            if (count == -1) break
            read += count
        }

        return RecordedRequest(
            method = requestLine.substringBefore(' '),
            contentType = headers.entries.firstOrNull {
                it.key.equals("Content-Type", ignoreCase = true)
            }?.value.orEmpty(),
            body = String(bodyChars, 0, read)
        )
    }

    private fun writeResponse(socket: Socket, responseCode: Int, responseBody: String) {
        val bodyBytes = responseBody.toByteArray(StandardCharsets.UTF_8)
        val output = socket.getOutputStream()
        output.write(
            (
                "HTTP/1.1 $responseCode OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${bodyBytes.size}\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
                ).toByteArray(StandardCharsets.UTF_8)
        )
        output.write(bodyBytes)
        output.flush()
    }

    private data class RecordedRequest(
        val method: String,
        val contentType: String,
        val body: String
    )
}
