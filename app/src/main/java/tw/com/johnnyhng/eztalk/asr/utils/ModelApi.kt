package tw.com.johnnyhng.eztalk.asr.utils

import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

internal data class RemoteModelUpdate(
    val modelName: String,
    val filename: String,
    val fileSizeBytes: Long,
    val serverHash: String,
    val userId: String
)

internal data class RemoteModelListResponse(
    val models: List<String>
)

internal data class RemoteModelListResult(
    val models: List<String>,
    val errorMessage: String? = null
)

internal data class ModelDownloadResult(
    val success: Boolean,
    val errorMessage: String? = null
)

internal fun buildListModelsUrl(baseUrl: String, userId: String): String =
    BackendEndpoints.listModels(baseUrl, userId)

internal fun buildCheckUpdateUrl(baseUrl: String, userId: String): String =
    BackendEndpoints.checkUpdate(baseUrl, userId)

internal fun buildModelFileUrl(
    baseUrl: String,
    userId: String,
    modelName: String,
    filename: String
): String = BackendEndpoints.downloadFile(baseUrl, userId, modelName, filename)

internal fun parseRemoteModelUpdate(
    responseBody: String,
    modelName: String
): RemoteModelUpdate {
    val json = JSONObject(responseBody)
    return RemoteModelUpdate(
        modelName = modelName,
        filename = json.getString("filename"),
        fileSizeBytes = json.optLong("file_size_bytes", 0L),
        serverHash = json.optString("server_hash", ""),
        userId = json.optString("user_id", "")
    )
}

internal fun parseRemoteModelList(responseBody: String): RemoteModelListResponse {
    val json = JSONObject(responseBody)
    val models = json.optJSONArray("models")
        ?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val modelName = array.optString(index).trim()
                    if (modelName.isNotBlank()) add(modelName)
                }
            }
        }
        .orEmpty()
    return RemoteModelListResponse(models = models)
}

internal fun listRemoteModels(
    baseUrl: String,
    userId: String
): List<String> {
    return listRemoteModelsResult(baseUrl, userId).models
}

internal fun listRemoteModelsResult(
    baseUrl: String,
    userId: String
): RemoteModelListResult {
    return try {
        val connection = executeRequestWithRedirects(
            endpoint = buildListModelsUrl(baseUrl, userId),
            method = "GET",
            connectTimeoutMs = 15000,
            readTimeoutMs = 15000
        ) ?: return RemoteModelListResult(emptyList(), "Unable to create remote model request")

        val responseCode = connection.responseCode
        if (!isSuccessfulResponse(responseCode)) {
            Log.e(TAG, "listRemoteModels failed. Response code: $responseCode, message: ${connection.responseMessage}")
            Log.e(TAG, "Error body: ${readStreamText(connection.errorStream)}")
            return RemoteModelListResult(
                models = emptyList(),
                errorMessage = "Remote model request failed with HTTP $responseCode"
            )
        }

        val responseBody = readStreamText(connection.inputStream)
            ?: return RemoteModelListResult(emptyList(), "Remote model response was empty")
        RemoteModelListResult(parseRemoteModelList(responseBody).models)
    } catch (e: Exception) {
        val detail = TLSExpireResolver.resolveMessage(e, "listRemoteModels failed")
        Log.e(TAG, "Exception during listRemoteModels: $detail", e)
        RemoteModelListResult(emptyList(), detail)
    }
}

internal fun checkModelUpdate(
    baseUrl: String,
    userId: String,
    modelName: String
): RemoteModelUpdate? {
    return try {
        val connection = executeRequestWithRedirects(
            endpoint = buildCheckUpdateUrl(baseUrl, userId),
            method = "GET",
            connectTimeoutMs = 15000,
            readTimeoutMs = 15000
        ) ?: return null

        val responseCode = connection.responseCode
        if (!isSuccessfulResponse(responseCode)) {
            Log.e(TAG, "checkModelUpdate failed. Response code: $responseCode, message: ${connection.responseMessage}")
            Log.e(TAG, "Error body: ${readStreamText(connection.errorStream)}")
            return null
        }

        val responseBody = readStreamText(connection.inputStream) ?: return null
        parseRemoteModelUpdate(responseBody, modelName)
    } catch (e: Exception) {
        val detail = TLSExpireResolver.resolveMessage(e, "checkModelUpdate failed")
        Log.e(TAG, "Exception during checkModelUpdate: $detail", e)
        null
    }
}

internal fun downloadModelFile(
    baseUrl: String,
    userId: String,
    modelName: String,
    filename: String,
    targetFile: File,
    onProgress: (Float?) -> Unit = {}
): Boolean {
    return downloadModelFileResult(
        baseUrl = baseUrl,
        userId = userId,
        modelName = modelName,
        filename = filename,
        targetFile = targetFile,
        onProgress = onProgress
    ).success
}

internal fun downloadModelFileResult(
    baseUrl: String,
    userId: String,
    modelName: String,
    filename: String,
    targetFile: File,
    onProgress: (Float?) -> Unit = {}
): ModelDownloadResult {
    return try {
        val connection = executeRequestWithRedirects(
            endpoint = buildModelFileUrl(baseUrl, userId, modelName, filename),
            method = "GET",
            connectTimeoutMs = 15000,
            readTimeoutMs = 15000
        ) ?: return ModelDownloadResult(false, "Unable to create model download request")

        val responseCode = connection.responseCode
        if (!isSuccessfulResponse(responseCode)) {
            Log.e(TAG, "downloadModelFile failed. Response code: $responseCode, message: ${connection.responseMessage}")
            Log.e(TAG, "Error body: ${readStreamText(connection.errorStream)}")
            return ModelDownloadResult(
                success = false,
                errorMessage = "Model download failed with HTTP $responseCode"
            )
        }

        targetFile.parentFile?.mkdirs()
        val contentLength = connection.contentLengthLong
        connection.inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (contentLength > 0L) {
                        onProgress((totalRead.toFloat() / contentLength).coerceIn(0f, 1f))
                    } else {
                        onProgress(null)
                    }
                }
            }
        }
        ModelDownloadResult(success = true)
    } catch (e: Exception) {
        val detail = TLSExpireResolver.resolveMessage(e, "downloadModelFile failed")
        Log.e(TAG, "Exception during downloadModelFile: $detail", e)
        ModelDownloadResult(success = false, errorMessage = detail)
    }
}
