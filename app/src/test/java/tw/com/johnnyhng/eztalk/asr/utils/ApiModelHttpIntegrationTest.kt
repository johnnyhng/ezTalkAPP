package tw.com.johnnyhng.eztalk.asr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class ApiModelHttpIntegrationTest {
    @Test
    fun listRemoteModelsParsesBackendResponse() {
        val result = withHttpServer(
            responseCode = 200,
            contentType = "application/json",
            responseBody = """{"models":["mobile","desktop"]}"""
        ) { serverUrl, requestRef ->
            val models = listRemoteModels("$serverUrl/api/v2", "tester@example.com")
            assertEquals("GET", requestRef.get().method)
            assertEquals("/api/v2/list_models/tester", requestRef.get().path)
            models
        }

        assertEquals(listOf("mobile", "desktop"), result)
    }

    @Test
    fun checkModelUpdateParsesDescriptorResponse() {
        val result = withHttpServer(
            responseCode = 200,
            contentType = "application/json",
            responseBody = """
                {
                  "file_size_bytes": 123,
                  "filename": "model.int8.onnx",
                  "server_hash": "abc123",
                  "user_id": "tester"
                }
            """.trimIndent()
        ) { serverUrl, requestRef ->
            val update = checkModelUpdate(
                baseUrl = "$serverUrl/api/v2",
                userId = "tester@example.com",
                modelName = "mobile"
            )
            assertEquals("GET", requestRef.get().method)
            assertEquals("/api/v2/check_update/tester", requestRef.get().path)
            update
        }

        assertNotNull(result)
        assertEquals("mobile", result?.modelName)
        assertEquals("model.int8.onnx", result?.filename)
        assertEquals(123L, result?.fileSizeBytes)
        assertEquals("abc123", result?.serverHash)
        assertEquals("tester", result?.userId)
    }

    @Test
    fun checkModelUpdateReturnsNullOnFailureResponse() {
        val result = withHttpServer(
            responseCode = 404,
            contentType = "application/json",
            responseBody = """{"error":"missing"}"""
        ) { serverUrl, _ ->
            checkModelUpdate(
                baseUrl = "$serverUrl/api/v2",
                userId = "tester@example.com",
                modelName = "mobile"
            )
        }

        assertNull(result)
    }

    @Test
    fun downloadModelFileWritesFileAndReportsProgress() {
        val targetFile = File(TestFixtures.tempDir("model-download"), "mobile/model.int8.onnx")
        val progressEvents = mutableListOf<Float?>()

        val success = withHttpServer(
            responseCode = 200,
            contentType = "application/octet-stream",
            responseBody = "model-bytes"
        ) { serverUrl, requestRef ->
            val downloaded = downloadModelFile(
                baseUrl = "$serverUrl/api/v2",
                userId = "tester@example.com",
                modelName = "mobile",
                filename = "model.int8.onnx",
                targetFile = targetFile,
                onProgress = { progressEvents += it }
            )
            assertEquals("GET", requestRef.get().method)
            assertEquals("/api/v2/files/tester/mobile/model.int8.onnx", requestRef.get().path)
            downloaded
        }

        assertTrue(success)
        assertTrue(targetFile.exists())
        assertEquals("model-bytes", targetFile.readText())
        assertFalse(progressEvents.isEmpty())
        assertEquals(1f, progressEvents.last())
    }

    @Test
    fun downloadModelFileReturnsFalseOnFailureResponse() {
        val targetFile = File(TestFixtures.tempDir("model-download-failure"), "mobile/model.int8.onnx")

        val success = withHttpServer(
            responseCode = 404,
            contentType = "application/json",
            responseBody = """{"error":"missing"}"""
        ) { serverUrl, _ ->
            downloadModelFile(
                baseUrl = "$serverUrl/api/v2",
                userId = "tester@example.com",
                modelName = "mobile",
                filename = "model.int8.onnx",
                targetFile = targetFile
            )
        }

        assertFalse(success)
        assertFalse(targetFile.exists())
    }

    private fun <T> withHttpServer(
        responseCode: Int,
        contentType: String,
        responseBody: String,
        block: (serverUrl: String, requestRef: AtomicReference<RecordedRequest>) -> T
    ): T {
        ServerSocket(0).use { serverSocket ->
            val requestRef = AtomicReference<RecordedRequest>()
            val latch = CountDownLatch(1)
            val acceptThread = Thread {
                serverSocket.accept().use { socket ->
                    requestRef.set(readRequest(socket))
                    writeResponse(socket, responseCode, contentType, responseBody)
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
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }
        return RecordedRequest(
            method = requestLine.substringBefore(' '),
            path = requestLine.substringAfter(' ').substringBefore(' ')
        )
    }

    private fun writeResponse(
        socket: Socket,
        responseCode: Int,
        contentType: String,
        responseBody: String
    ) {
        val bodyBytes = responseBody.toByteArray(StandardCharsets.UTF_8)
        val output = socket.getOutputStream()
        output.write(
            (
                "HTTP/1.1 $responseCode OK\r\n" +
                    "Content-Type: $contentType\r\n" +
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
        val path: String
    )
}
