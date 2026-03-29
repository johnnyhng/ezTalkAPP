package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures
import java.io.File

class ApiRequestPlanTest {
    @Test
    fun buildProcessAudioRequestPlanBuildsJsonPlanWithOptionalRaw() {
        val plan = buildProcessAudioRequestPlan(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadata = JSONObject().apply { put("modified", "text") },
            sendFileByJson = true,
            rawReader = { JSONArray().apply { put(1); put(2) } }
        )

        assertTrue(plan is UploadRequestPlan.Json)
        val payload = (plan as UploadRequestPlan.Json).payload
        assertEquals("text", payload.getString("label"))
        assertEquals(2, payload.getJSONArray("raw").length())
    }

    @Test
    fun buildProcessAudioRequestPlanBuildsMultipartPlanWhenFileExists() {
        val file = File(TestFixtures.tempDir("process-plan"), "sample.wav").apply {
            writeBytes(ByteArray(44))
        }

        val plan = buildProcessAudioRequestPlan(
            filePath = file.absolutePath,
            userId = "tester@example.com",
            sendFileByJson = false
        )

        assertTrue(plan is UploadRequestPlan.Multipart)
        val multipart = plan as UploadRequestPlan.Multipart
        assertEquals(file.absolutePath, multipart.file.absolutePath)
        assertFalse(multipart.payload.has("raw"))
    }

    @Test
    fun buildProcessAudioRequestPlanReturnsNullWhenMultipartFileIsMissing() {
        val plan = buildProcessAudioRequestPlan(
            filePath = "/tmp/missing.wav",
            userId = "tester@example.com",
            sendFileByJson = false
        )

        assertNull(plan)
    }

    @Test
    fun buildTransferRequestPlanBuildsJsonPlanWhenUploadJsonExists() {
        val plan = buildTransferRequestPlan(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            sendFileByJson = true,
            uploadJsonBuilder = { _, _ -> JSONObject().apply { put("label", "x") } }
        )

        assertTrue(plan is UploadRequestPlan.Json)
        assertEquals("x", (plan as UploadRequestPlan.Json).payload.getString("label"))
    }

    @Test
    fun buildTransferRequestPlanReturnsNullWhenJsonPayloadCannotBeBuilt() {
        val plan = buildTransferRequestPlan(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            sendFileByJson = true,
            uploadJsonBuilder = { _, _ -> null }
        )

        assertNull(plan)
    }

    @Test
    fun buildTransferRequestPlanBuildsMultipartPlanWhenMetadataAndFileExist() {
        val file = File(TestFixtures.tempDir("transfer-plan"), "sample.wav").apply {
            writeBytes(ByteArray(44))
        }

        val plan = buildTransferRequestPlan(
            filePath = file.absolutePath,
            userId = "tester@example.com",
            sendFileByJson = false,
            metadataBuilder = { _, _ -> JSONObject().apply { put("sentence", "x") } }
        )

        assertTrue(plan is UploadRequestPlan.Multipart)
        val multipart = plan as UploadRequestPlan.Multipart
        assertEquals("x", multipart.payload.getString("sentence"))
        assertEquals(file.absolutePath, multipart.file.absolutePath)
    }

    @Test
    fun buildTransferRequestPlanReturnsNullWhenMetadataIsMissingOrFileDoesNotExist() {
        val missingMetadata = buildTransferRequestPlan(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            sendFileByJson = false,
            metadataBuilder = { _, _ -> null }
        )
        val missingFile = buildTransferRequestPlan(
            filePath = "/tmp/missing.wav",
            userId = "tester@example.com",
            sendFileByJson = false,
            metadataBuilder = { _, _ -> JSONObject() }
        )

        assertNull(missingMetadata)
        assertNull(missingFile)
    }

    @Test
    fun buildRecognitionRequestPlanBuildsJsonAndMultipartModes() {
        val jsonPlan = buildRecognitionRequestPlan(
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            sendFileByJson = true,
            rawReader = { JSONArray().apply { put(7) } }
        )
        val file = File(TestFixtures.tempDir("recognition-plan"), "sample.wav").apply {
            writeBytes(ByteArray(44))
        }
        val multipartPlan = buildRecognitionRequestPlan(
            filePath = file.absolutePath,
            userId = "tester@example.com",
            sendFileByJson = false
        )

        assertTrue(jsonPlan is UploadRequestPlan.Json)
        assertEquals(1, (jsonPlan as UploadRequestPlan.Json).payload.getJSONArray("raw").length())
        assertTrue(multipartPlan is UploadRequestPlan.Multipart)
        assertEquals(file.absolutePath, (multipartPlan as UploadRequestPlan.Multipart).file.absolutePath)
    }

    @Test
    fun buildRecognitionRequestPlanReturnsNullWhenMultipartFileIsMissing() {
        val plan = buildRecognitionRequestPlan(
            filePath = "/tmp/missing.wav",
            userId = "tester@example.com",
            sendFileByJson = false
        )

        assertNull(plan)
    }

    @Test
    fun isSuccessfulResponseOnlyTreatsHttpOkAsSuccess() {
        assertTrue(isSuccessfulResponse(200))
        assertFalse(isSuccessfulResponse(201))
        assertFalse(isSuccessfulResponse(500))
    }
}
