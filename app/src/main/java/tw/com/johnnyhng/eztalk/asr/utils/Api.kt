package tw.com.johnnyhng.eztalk.asr.utils

import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection

internal enum class FeedbackRoute {
    PUT_UPDATES,
    POST_PROCESS_AUDIO,
    POST_TRANSFER
}

internal sealed class UploadRequestPlan {
    data class Json(val payload: JSONObject) : UploadRequestPlan()
    data class Multipart(val file: File, val payload: JSONObject) : UploadRequestPlan()
}

internal data class FeedbackDispatchPlan(
    val route: FeedbackRoute,
    val endpoint: String
)

internal data class PackagedUploadJson(
    val metadata: JSONObject,
    val raw: JSONArray
)

internal data class UploadMetadataSnapshot(
    val label: String,
    val remoteCandidates: JSONArray
)

internal data class MultipartRequestContent(
    val contentType: String,
    val body: ByteArray
)

internal data class UploadMetadataSource(
    val wavFile: File,
    val jsonlFile: File
)

internal data class FeedbackExecution(
    val jsonlPath: String,
    val metadata: JSONObject?,
    val dispatchPlan: FeedbackDispatchPlan
)


/**
 * Reads a WAV file and returns its content as a JSONArray of bytes.
 *
 * @param path The absolute path to the WAV file.
 * @return A JSONArray containing the byte values of the file, or null on error.
 */
internal fun readWavFileToJsonArray(path: String): JSONArray? {
    try {
        val rawArray = readWavJsonArray(
            wavFile = File(path),
            byteReader = { file ->
                FileInputStream(file).use { it.readBytes() }
            }
        )
        if (rawArray == null) {
            Log.e(TAG, "WAV file is too small to contain a valid header: ${File(path).name}")
            return null
        }
        return rawArray
    } catch (e: Exception) {
        Log.e(TAG, "Error reading WAV file to JSONArray: $path", e)
        return null
    }
}

internal fun readWavJsonArray(
    wavFile: File,
    byteReader: (File) -> ByteArray
): JSONArray? = wavBytesToJsonArray(byteReader(wavFile))

internal fun wavBytesToJsonArray(byteBuffer: ByteArray): JSONArray? {
    val headerSize = 44
    if (byteBuffer.size < headerSize) {
        return null
    }

    return JSONArray().apply {
        byteBuffer.forEach {
            put(it.toInt() and 0xff)
        }
    }
}

internal fun packageUploadJsonMetadata(path: String, userId: String): JSONObject? {
    try {
        val source = buildUploadMetadataSource(path)
        val snapshot = readUploadMetadataSnapshot(
            jsonlFile = source.jsonlFile,
            jsonlReader = { it.readText() }
        )

        return buildUploadJsonMetadata(
            filename = source.wavFile.name,
            userId = userId,
            label = snapshot.label,
            remoteCandidates = snapshot.remoteCandidates
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error packaging upload JSON metadata for $path", e)
        return null
    }
}

internal fun buildUploadMetadataSource(path: String): UploadMetadataSource =
    UploadMetadataSource(
        wavFile = File(path),
        jsonlFile = File(path.substringBeforeLast(".") + ".jsonl")
    )

internal fun readUploadMetadataSnapshot(
    jsonlFile: File,
    jsonlReader: (File) -> String
): UploadMetadataSnapshot {
    if (!jsonlFile.exists()) {
        Log.w(TAG, "jsonl file not found for wav: ${jsonlFile.path}")
        return UploadMetadataSnapshot("", JSONArray())
    }

    return try {
        val jsonlContent = jsonlReader(jsonlFile)
        if (jsonlContent.isBlank()) {
            UploadMetadataSnapshot("", JSONArray())
        } else {
            parseUploadMetadataSnapshot(jsonlContent)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error reading or parsing jsonl file: ${jsonlFile.path}", e)
        UploadMetadataSnapshot("", JSONArray())
    }
}

internal fun parseUploadMetadataSnapshot(jsonlContent: String): UploadMetadataSnapshot {
    val jsonObject = JSONObject(jsonlContent)
    return UploadMetadataSnapshot(
        label = jsonObject.optString("modified", ""),
        remoteCandidates = jsonObject.optJSONArray("remote_candidates") ?: JSONArray()
    )
}

internal fun buildUploadJsonMetadata(
    filename: String,
    userId: String,
    label: String,
    remoteCandidates: JSONArray = JSONArray()
): JSONObject {
    val account = JSONObject().apply {
        put("user_id", userId.split("@")[0])
    }

    return JSONObject().apply {
        put("account", account)
        put("label", label)
        put("sentence", label)
        put("filename", filename)
        put("charMode", false)
        put("remote_candidates", remoteCandidates)
    }
}

internal fun buildMergedCandidates(metadata: JSONObject?): JSONArray {
    if (metadata == null) return JSONArray()

    val merged = (
        metadata.optStringList("local_candidates") +
            metadata.optStringList("remote_candidates")
        ).distinct()
    return JSONArray(merged)
}

internal fun decideFeedbackRoute(
    metadata: JSONObject?,
    recognitionUrl: String
): FeedbackRoute {
    val hasRemoteCandidates = metadata?.optStringList("remote_candidates").orEmpty().isNotEmpty()
    val hasLocalCandidates = metadata?.optStringList("local_candidates").orEmpty().isNotEmpty()

    return when {
        hasRemoteCandidates -> FeedbackRoute.PUT_UPDATES
        hasLocalCandidates && recognitionUrl.isNotBlank() -> FeedbackRoute.POST_PROCESS_AUDIO
        else -> FeedbackRoute.POST_TRANSFER
    }
}

internal fun buildProcessAudioPayload(
    filePath: String,
    userId: String,
    metadata: JSONObject? = null,
    raw: JSONArray? = null
): JSONObject {
    return JSONObject().apply {
        put("login_user", userId.split("@")[0])
        put("filename", filePath.substringAfterLast("/"))
        put("label", metadata?.optString("modified") ?: "tmp")
        put("num_of_stn", 8)
        raw?.let { put("raw", it) }
    }
}

internal fun buildRecognitionPayload(
    filePath: String,
    userId: String,
    raw: JSONArray? = null
): JSONObject {
    return JSONObject().apply {
        put("login_user", userId.split("@")[0])
        put("filename", filePath.substringAfterLast("/"))
        put("label", "tmp")
        put("num_of_stn", 8)
        raw?.let { put("raw", it) }
    }
}

internal fun buildUpdatePayload(
    filePath: String,
    userId: String,
    metadata: JSONObject? = null
): JSONObject {
    val account = JSONObject().apply {
        put("user_id", userId.split("@")[0])
        put("password", sha256(sha256("password")))
    }

    val label = JSONObject().apply {
        put("original", "tmp")
        put("modified", metadata?.optString("modified") ?: "")
        put("candidates", buildMergedCandidates(metadata))
    }

    val streamFilesMove = JSONArray().apply {
        put(JSONObject().apply {
            put(filePath.substringAfterLast("/"), label)
        })
    }

    return JSONObject().apply {
        put("account", account)
        put("streamFilesMove", streamFilesMove)
        put("update_files", "True")
        put("sentence", metadata?.optString("modified") ?: "")
    }
}

internal fun isSuccessfulResponse(responseCode: Int): Boolean {
    return responseCode == HttpURLConnection.HTTP_OK
}

internal fun buildFeedbackDispatchPlan(
    backendUrl: String,
    recognitionUrl: String,
    metadata: JSONObject?
): FeedbackDispatchPlan {
    val route = decideFeedbackRoute(metadata, recognitionUrl)
    val endpoint = when (route) {
        FeedbackRoute.PUT_UPDATES -> "$backendUrl/api/updates"
        FeedbackRoute.POST_PROCESS_AUDIO -> recognitionUrl
        FeedbackRoute.POST_TRANSFER -> "$backendUrl/api/transfer"
    }
    return FeedbackDispatchPlan(route = route, endpoint = endpoint)
}

internal fun combineUploadJson(
    metadata: JSONObject?,
    rawArray: JSONArray?
): JSONObject? {
    if (metadata == null || rawArray == null) return null
    return JSONObject(metadata.toString()).apply {
        put("raw", rawArray)
    }
}

internal fun buildPackagedUploadJson(
    metadata: JSONObject?,
    rawArray: JSONArray?
): PackagedUploadJson? {
    if (metadata == null || rawArray == null) return null
    return PackagedUploadJson(metadata = metadata, raw = rawArray)
}

internal fun buildMultipartRequestContent(
    jsonPayload: JSONObject,
    file: File,
    boundary: String
): MultipartRequestContent {
    val body = ByteArrayOutputStream().use { output ->
        DataOutputStream(output).use { dos ->
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            dos.writeBytes(twoHyphens + boundary + lineEnd)
            dos.writeBytes("Content-Disposition: form-data; name=\"json\"$lineEnd")
            dos.writeBytes("Content-Type: application/json; charset=UTF-8$lineEnd")
            dos.writeBytes(lineEnd)
            dos.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            dos.writeBytes(lineEnd)

            dos.writeBytes(twoHyphens + boundary + lineEnd)
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"$lineEnd")
            dos.writeBytes("Content-Type: audio/wav$lineEnd")
            dos.writeBytes(lineEnd)

            FileInputStream(file).use { fis ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    dos.write(buffer, 0, bytesRead)
                }
            }
            dos.writeBytes(lineEnd)

            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
        }
        output.toByteArray()
    }

    return MultipartRequestContent(
        contentType = "multipart/form-data; boundary=$boundary",
        body = body
    )
}

internal fun buildJsonRequestContent(payload: JSONObject): ByteArray =
    payload.toString().toByteArray(Charsets.UTF_8)

internal fun writeUploadRequest(
    connection: HttpURLConnection,
    plan: UploadRequestPlan,
    boundaryProvider: () -> String = { "Boundary-${System.currentTimeMillis()}" }
) {
    when (plan) {
        is UploadRequestPlan.Json -> {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { os ->
                os.write(buildJsonRequestContent(plan.payload))
            }
        }
        is UploadRequestPlan.Multipart -> {
            val requestContent = buildMultipartRequestContent(
                jsonPayload = plan.payload,
                file = plan.file,
                boundary = boundaryProvider()
            )
            connection.setRequestProperty("Content-Type", requestContent.contentType)
            connection.outputStream.use { os ->
                os.write(requestContent.body)
            }
        }
    }
}

internal fun readStreamText(inputStream: InputStream?): String? =
    inputStream?.bufferedReader()?.use { it.readText() }

internal fun parseRecognitionResponseBody(responseBody: String): JSONObject? =
    JSONObject(responseBody).optJSONObject("response")

internal fun buildProcessAudioRequestPlan(
    filePath: String,
    userId: String,
    metadata: JSONObject? = null,
    sendFileByJson: Boolean,
    rawReader: (String) -> JSONArray? = ::readWavFileToJsonArray
): UploadRequestPlan? {
    return if (sendFileByJson) {
        UploadRequestPlan.Json(
            buildProcessAudioPayload(
                filePath = filePath,
                userId = userId,
                metadata = metadata,
                raw = rawReader(filePath)
            )
        )
    } else {
        val file = File(filePath)
        if (!file.exists()) return null
        UploadRequestPlan.Multipart(
            file = file,
            payload = buildProcessAudioPayload(
                filePath = filePath,
                userId = userId,
                metadata = metadata
            )
        )
    }
}

internal fun buildTransferRequestPlan(
    filePath: String,
    userId: String,
    sendFileByJson: Boolean,
    uploadJsonBuilder: (String, String) -> JSONObject? = ::packageUploadJson,
    metadataBuilder: (String, String) -> JSONObject? = ::packageUploadJsonMetadata
): UploadRequestPlan? {
    return if (sendFileByJson) {
        uploadJsonBuilder(filePath, userId)?.let(UploadRequestPlan::Json)
    } else {
        val metadata = metadataBuilder(filePath, userId) ?: return null
        val file = File(filePath)
        if (!file.exists()) return null
        UploadRequestPlan.Multipart(file = file, payload = metadata)
    }
}

internal fun buildRecognitionRequestPlan(
    filePath: String,
    userId: String,
    sendFileByJson: Boolean,
    rawReader: (String) -> JSONArray? = ::readWavFileToJsonArray
): UploadRequestPlan? {
    return if (sendFileByJson) {
        UploadRequestPlan.Json(
            buildRecognitionPayload(
                filePath = filePath,
                userId = userId,
                raw = rawReader(filePath)
            )
        )
    } else {
        val file = File(filePath)
        if (!file.exists()) return null
        UploadRequestPlan.Multipart(
            file = file,
            payload = buildRecognitionPayload(
                filePath = filePath,
                userId = userId
            )
        )
    }
}

/**
 * Packages a WAV file and its metadata into a JSON object for uploading.
 *
 * @param path The absolute path to the WAV file.
 * @param userId The ID of the user.
 * @return A JSONObject ready for upload, or null on error.
 */
fun packageUploadJson(path: String, userId: String): JSONObject? {
    val packaged = buildUploadPackage(
        path = path,
        userId = userId,
        metadataLoader = ::packageUploadJsonMetadata,
        rawLoader = ::readWavFileToJsonArray
    ) ?: return null
    return combineUploadJson(packaged.metadata, packaged.raw)
}

internal fun buildUploadPackage(
    path: String,
    userId: String,
    metadataLoader: (String, String) -> JSONObject?,
    rawLoader: (String) -> JSONArray?
): PackagedUploadJson? {
    val metadata = metadataLoader(path, userId) ?: return null
    val rawArray = rawLoader(path) ?: return null
    return buildPackagedUploadJson(metadata, rawArray)
}

internal fun executeFeedbackDispatch(
    dispatchPlan: FeedbackDispatchPlan,
    filePath: String,
    userId: String,
    metadata: JSONObject?,
    putUpdates: (String, String, String, JSONObject?) -> Boolean,
    postProcessAudio: (String, String, String, JSONObject?) -> Boolean,
    postTransfer: (String, String, String) -> Boolean
): Boolean {
    return when (dispatchPlan.route) {
        FeedbackRoute.PUT_UPDATES -> putUpdates(dispatchPlan.endpoint, filePath, userId, metadata)
        FeedbackRoute.POST_PROCESS_AUDIO -> postProcessAudio(dispatchPlan.endpoint, filePath, userId, metadata)
        FeedbackRoute.POST_TRANSFER -> postTransfer(dispatchPlan.endpoint, filePath, userId)
    }
}

fun feedbackToBackend(
    backendUrl: String,
    recognitionUrl: String,
    filePath: String,
    userId: String
): Boolean {
    val execution = buildFeedbackExecution(
        backendUrl = backendUrl,
        recognitionUrl = recognitionUrl,
        filePath = filePath,
        metadataReader = ::readJsonl
    )

    Log.d(
        TAG,
        "feedbackToBackend: filePath=$filePath, jsonlPath=${execution.jsonlPath}, route=${execution.dispatchPlan.route}"
    )

    return executeFeedbackDispatch(
        dispatchPlan = execution.dispatchPlan,
        filePath = filePath,
        userId = userId,
        metadata = execution.metadata,
        putUpdates = { endpoint, path, id, data ->
            Log.d(TAG, "feedbackToBackend: using PUT /api/updates")
            putForUpdates(endpoint, path, id, data)
        },
        postProcessAudio = { endpoint, path, id, data ->
            Log.d(TAG, "feedbackToBackend: using POST process_audio")
            postProcessAudio(endpoint, path, id, data)
        },
        postTransfer = { endpoint, path, id ->
            Log.d(TAG, "feedbackToBackend: using POST /api/transfer")
            postTransfer(endpoint, path, id)
        }
    )
}

internal fun buildFeedbackExecution(
    backendUrl: String,
    recognitionUrl: String,
    filePath: String,
    metadataReader: (String) -> JSONObject?
): FeedbackExecution {
    val jsonlPath = filePath.substringBeforeLast(".") + ".jsonl"
    val metadata = metadataReader(jsonlPath)
    val dispatchPlan = buildFeedbackDispatchPlan(
        backendUrl = backendUrl,
        recognitionUrl = recognitionUrl,
        metadata = metadata
    )
    return FeedbackExecution(
        jsonlPath = jsonlPath,
        metadata = metadata,
        dispatchPlan = dispatchPlan
    )
}

fun postProcessAudio(
    processAudioUrl: String,
    filePath: String,
    userId: String,
    metadata: JSONObject? = null,
    sendFileByJson: Boolean = true
): Boolean {
    return try {
        Log.d(
            TAG,
            "postProcessAudio: url=$processAudioUrl, filePath=$filePath, sendFileByJson=$sendFileByJson"
        )
        val url = URL(processAudioUrl)
        val connection = url.openConnection() as HttpURLConnection
        val hostnameVerifier = HostnameVerifier { _, _ -> true }
        (connection as? HttpsURLConnection)?.hostnameVerifier = hostnameVerifier

        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        when (val plan = buildProcessAudioRequestPlan(
            filePath = filePath,
            userId = userId,
            metadata = metadata,
            sendFileByJson = sendFileByJson
        )) {
            is UploadRequestPlan.Json,
            is UploadRequestPlan.Multipart -> writeUploadRequest(connection, plan)
            null -> {
                Log.e(TAG, "File not found for upload: $filePath")
                return false
            }
        }

        val responseCode = connection.responseCode
        if (isSuccessfulResponse(responseCode)) {
            Log.d(TAG, "postProcessAudio: success, responseCode=$responseCode")
            true
        } else {
            Log.e(TAG, "process_audio post failed. Response code: $responseCode, message: ${connection.responseMessage}")
            Log.e(TAG, "Error body: ${readStreamText(connection.errorStream)}")
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, "Exception during process_audio post", e)
        false
    }
}

fun putForUpdates(
    updateUrl: String,
    filePath: String,
    userId: String,
    metadata: JSONObject? = null
): Boolean {
    return try {
        Log.d(
            TAG,
            "putForUpdates: url=$updateUrl, filePath=$filePath, sentence=${metadata?.optString("modified") ?: ""}"
        )
        val url = URL(updateUrl)
        val connection = url.openConnection() as HttpURLConnection
        val hostnameVerifier = HostnameVerifier { _, _ -> true }
        (connection as? HttpsURLConnection)?.hostnameVerifier = hostnameVerifier

        connection.requestMethod = "PUT"
        connection.doOutput = true
        connection.connectTimeout = 5000 // 5 seconds
        connection.readTimeout = 5000 // 5 seconds

        val jsonPayload = buildUpdatePayload(
            filePath = filePath,
            userId = userId,
            metadata = metadata
        )

        writeUploadRequest(connection, UploadRequestPlan.Json(jsonPayload))

        val responseCode = connection.responseCode
        if (isSuccessfulResponse(responseCode)) {
            Log.d(TAG, "putForUpdates: success, responseCode=$responseCode")
            true
        } else {
            Log.e(TAG, "Update failed. Response code: $responseCode, message: ${connection.responseMessage}")
            Log.e(TAG, "Error body: ${readStreamText(connection.errorStream)}")
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, "Exception during update", e)
        false
    }
}

fun postTransfer(
    transferUrl: String,
    filePath: String,
    userId: String,
    sendFileByJson: Boolean = true
): Boolean {
    return try {
        Log.d(
            TAG,
            "postTransfer: url=$transferUrl, filePath=$filePath, sendFileByJson=$sendFileByJson"
        )
        val url = URL(transferUrl)
        val connection = url.openConnection() as HttpURLConnection
        val hostnameVerifier = HostnameVerifier { _, _ -> true }
        (connection as? HttpsURLConnection)?.hostnameVerifier = hostnameVerifier

        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 5000 // 5 seconds
        connection.readTimeout = 5000 // 5 seconds

        when (val plan = buildTransferRequestPlan(
            filePath = filePath,
            userId = userId,
            sendFileByJson = sendFileByJson
        )) {
            is UploadRequestPlan.Json,
            is UploadRequestPlan.Multipart -> writeUploadRequest(connection, plan)
            null -> {
                Log.e(TAG, "Failed to create metadata for $filePath")
                return false
            }
        }

        val responseCode = connection.responseCode
        if (isSuccessfulResponse(responseCode)) {
            Log.d(TAG, "postTransfer: success, responseCode=$responseCode")
            true
        } else {
            Log.e(TAG, "Transfer post failed. Response code: $responseCode, message: ${connection.responseMessage}")
            Log.e(TAG, "Error body: ${readStreamText(connection.errorStream)}")
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, "Exception during transfer post", e)
        false
    }
}

fun postForRecognition(
    recognitionUrl: String,
    filePath: String,
    userId: String,
    sendFileByJson: Boolean = true
): JSONObject? {
    return try {
        val url = URL(recognitionUrl)
        val connection = url.openConnection() as HttpURLConnection
        val hostnameVerifier = HostnameVerifier { _, _ -> true }
        (connection as? HttpsURLConnection)?.hostnameVerifier = hostnameVerifier

        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15000 // 15 seconds
        connection.readTimeout = 15000 // 15 seconds

        when (val plan = buildRecognitionRequestPlan(
            filePath = filePath,
            userId = userId,
            sendFileByJson = sendFileByJson
        )) {
            is UploadRequestPlan.Json,
            is UploadRequestPlan.Multipart -> writeUploadRequest(connection, plan)
            null -> {
                Log.e(TAG, "File not found for upload: $filePath")
                return null
            }
        }

        val responseCode = connection.responseCode
        if (isSuccessfulResponse(responseCode)) {
            parseRecognitionResponseBody(readStreamText(connection.inputStream) ?: "")
        } else {
            Log.e(TAG, "Recognition post failed. Response code: $responseCode, message: ${connection.responseMessage}")
            Log.e(TAG, "Error body: ${readStreamText(connection.errorStream)}")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Exception during recognition post", e)
        null
    } as JSONObject?
}
