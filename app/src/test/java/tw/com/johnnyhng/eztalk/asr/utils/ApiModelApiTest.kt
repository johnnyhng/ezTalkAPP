package tw.com.johnnyhng.eztalk.asr.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiModelApiTest {
    @Test
    fun apiBaseUrlUsesUserInputAsIs() {
        assertEquals(
            "https://example.com/api",
            BackendEndpoints.apiBaseUrl("https://example.com/api")
        )
    }

    @Test
    fun apiBaseUrlOnlyRemovesTrailingSlash() {
        assertEquals(
            "https://example.com/api/v2",
            BackendEndpoints.apiBaseUrl("https://example.com/api/v2/")
        )
    }

    @Test
    fun buildCheckUpdateUrlUsesBaseAndUserPrefix() {
        assertEquals(
            "https://example.com/api/check_update/tester",
            buildCheckUpdateUrl("https://example.com/api", "tester@example.com")
        )
    }

    @Test
    fun buildListModelsUrlUsesBaseAndUserPrefix() {
        assertEquals(
            "https://example.com/api/list_models/tester",
            buildListModelsUrl("https://example.com/api", "tester@example.com")
        )
    }

    @Test
    fun buildModelFileUrlUsesBaseAndModelPath() {
        assertEquals(
            "https://example.com/api/files/tester/custom-sense-voice/model.int8.onnx",
            buildModelFileUrl(
                baseUrl = "https://example.com/api",
                userId = "tester@example.com",
                modelName = "custom-sense-voice",
                filename = "model.int8.onnx"
            )
        )
    }

    @Test
    fun parseRemoteModelUpdateBuildsDescriptorPayload() {
        val update = parseRemoteModelUpdate(
            responseBody = """
                {
                  "file_size_bytes": 239233840,
                  "filename": "model.int8.onnx",
                  "server_hash": "57ab111e77e",
                  "user_id": "tester"
                }
            """.trimIndent(),
            modelName = "custom-sense-voice"
        )

        assertEquals("custom-sense-voice", update.modelName)
        assertEquals("model.int8.onnx", update.filename)
        assertEquals(239233840L, update.fileSizeBytes)
        assertEquals("57ab111e77e", update.serverHash)
        assertEquals("tester", update.userId)
    }

    @Test
    fun parseRemoteModelListBuildsModelNames() {
        val response = parseRemoteModelList(
            """
                {
                  "models": ["mobile", "desktop", ""]
                }
            """.trimIndent()
        )

        assertEquals(listOf("mobile", "desktop"), response.models)
    }
}
