package tw.com.johnnyhng.eztalk.asr.llm

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal data class GeminiApiResult(
    val responseCode: Int,
    val responseBody: String
)

internal class GeminiApiClient(
    private val endpoint: String
) {
    fun generateContent(
        model: String,
        accessToken: String,
        request: LlmRequest
    ): Result<GeminiApiResult> {
        return runCatching {
            val connection = openConnection(
                url = buildGenerateContentUrl(model),
                accessToken = accessToken
            )

            try {
                connection.outputStream.use { output ->
                    output.write(
                        buildRequestBody(request).toString().toByteArray(StandardCharsets.UTF_8)
                    )
                }

                GeminiApiResult(
                    responseCode = connection.responseCode,
                    responseBody = readResponseBody(connection)
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun buildGenerateContentUrl(model: String): String {
        val sanitizedEndpoint = endpoint.trimEnd('/')
        return "$sanitizedEndpoint/v1beta/models/$model:generateContent"
    }

    private fun openConnection(
        url: String,
        accessToken: String
    ): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun buildRequestBody(request: LlmRequest): JSONObject {
        val userParts = JSONArray().apply {
            put(JSONObject().put("text", request.userPrompt))
        }

        val contents = JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", userParts)
            )
        }

        return JSONObject().apply {
            put("contents", contents)

            request.systemInstruction
                ?.takeIf { it.isNotBlank() }
                ?.let { instruction ->
                    put(
                        "system_instruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", instruction))
                        )
                    )
                }

            buildGenerationConfig(request)?.let { config ->
                put("generationConfig", config)
            }
        }
    }

    private fun buildGenerationConfig(request: LlmRequest): JSONObject? {
        if (request.outputFormat != LlmOutputFormat.JSON && request.schemaHint.isNullOrBlank()) {
            return null
        }

        return JSONObject().apply {
            if (request.outputFormat == LlmOutputFormat.JSON) {
                put("responseMimeType", "application/json")
            }
        }
    }

    private fun readResponseBody(connection: HttpURLConnection): String {
        val stream = when {
            connection.responseCode >= 400 -> connection.errorStream
            else -> connection.inputStream
        }

        return stream?.use(::readFully).orEmpty()
    }

    private fun readFully(stream: InputStream): String {
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            buildString {
                var line = reader.readLine()
                while (line != null) {
                    append(line)
                    line = reader.readLine()
                }
            }
        }
    }
}
