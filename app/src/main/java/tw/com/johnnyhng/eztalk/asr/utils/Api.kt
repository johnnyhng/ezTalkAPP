package tw.com.johnnyhng.eztalk.asr.utils

import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
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


/**
 * Reads a WAV file and returns its content as a JSONArray of bytes.
 *
 * @param path The absolute path to the WAV file.
 * @return A JSONArray containing the byte values of the file, or null on error.
 */
internal fun readWavFileToJsonArray(path: String): JSONArray? {
    try {
        val wavFile = File(path)
        val fileInputStream = FileInputStream(wavFile)
        val byteBuffer = fileInputStream.readBytes()
        fileInputStream.close()

        val headerSize = 44
        if (byteBuffer.size < headerSize) {
            Log.e(TAG, "WAV file is too small to contain a valid header: ${wavFile.name}")
            return null
        }

        val rawArray = JSONArray()
        byteBuffer.forEach {
            rawArray.put(it.toInt() and 0xff)
        }
        return rawArray
    } catch (e: Exception) {
        Log.e(TAG, "Error reading WAV file to JSONArray: $path", e)
        return null
    }
}

internal fun packageUploadJsonMetadata(path: String, userId: String): JSONObject? {
    try {
        val wavFile = File(path)
        val jsonlPath = path.substringBeforeLast(".") + ".jsonl"
        val jsonlFile = File(jsonlPath)
        var label = ""
        var remoteCandidates = JSONArray()
        if (jsonlFile.exists()) {
            try {
                val jsonlContent = jsonlFile.readText()
                if (jsonlContent.isNotBlank()) {
                    val jsonObject = JSONObject(jsonlContent)
                    label = jsonObject.optString("modified", "")
                    remoteCandidates = jsonObject.optJSONArray("remote_candidates") ?: JSONArray()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading or parsing jsonl file: $jsonlPath", e)
            }
        } else {
            Log.w(TAG, "jsonl file not found for wav: $path")
        }

        val account = JSONObject()
        account.put("user_id", userId.split("@")[0])

        val json = JSONObject()
        json.put("account", account)
        json.put("label", label) // key in output json is "label"
        json.put("sentence", label)
        json.put("filename", wavFile.name)
        json.put("charMode", false)
        json.put("remote_candidates", remoteCandidates)

        return json
    } catch (e: Exception) {
        Log.e(TAG, "Error packaging upload JSON metadata for $path", e)
        return null
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
    val metadata = packageUploadJsonMetadata(path, userId) ?: return null
    val rawArray = readWavFileToJsonArray(path) ?: return null
    metadata.put("raw", rawArray)
    return metadata
}

fun feedbackToBackend(
    backendUrl: String,
    recognitionUrl: String,
    filePath: String,
    userId: String
): Boolean {
    val jsonlPath = filePath.substringBeforeLast(".") + ".jsonl"
    val metadata = readJsonl(jsonlPath)
    val route = decideFeedbackRoute(metadata, recognitionUrl)

    Log.d(
        TAG,
        "feedbackToBackend: filePath=$filePath, jsonlPath=$jsonlPath, route=$route"
    )

    return when (route) {
        FeedbackRoute.PUT_UPDATES -> {
            Log.d(TAG, "feedbackToBackend: using PUT /api/updates")
            putForUpdates("$backendUrl/api/updates", filePath, userId, metadata)
        }
        FeedbackRoute.POST_PROCESS_AUDIO -> {
            Log.d(TAG, "feedbackToBackend: using POST process_audio")
            postProcessAudio(recognitionUrl, filePath, userId, metadata)
        }
        FeedbackRoute.POST_TRANSFER -> {
            Log.d(TAG, "feedbackToBackend: using POST /api/transfer")
            postTransfer("$backendUrl/api/transfer", filePath, userId)
        }
    }
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
            is UploadRequestPlan.Json -> {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { os ->
                    val input = plan.payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
            }
            is UploadRequestPlan.Multipart -> {
                val file = plan.file
                val jsonPayload = plan.payload
                val boundary = "Boundary-${System.currentTimeMillis()}"
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                DataOutputStream(connection.outputStream).use { dos ->
                    val lineEnd = "\r\n"
                    val twoHyphens = "--"

                    dos.writeBytes(twoHyphens + boundary + lineEnd)
                    dos.writeBytes("Content-Disposition: form-data; name=\"json\"$lineEnd")
                    dos.writeBytes("Content-Type: application/json; charset=UTF-8$lineEnd")
                    dos.writeBytes(lineEnd)
                    dos.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
                    dos.writeBytes(lineEnd)

                    dos.writeBytes(twoHyphens + boundary + lineEnd)
                    dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"" + lineEnd)
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
            }
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
            val errorStream = connection.errorStream?.bufferedReader()?.readText()
            Log.e(TAG, "Error body: $errorStream")
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

        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.outputStream.use { os ->
            val input = jsonPayload.toString().toByteArray(Charsets.UTF_8)
            os.write(input, 0, input.size)
        }

        val responseCode = connection.responseCode
        if (isSuccessfulResponse(responseCode)) {
            Log.d(TAG, "putForUpdates: success, responseCode=$responseCode")
            true
        } else {
            Log.e(TAG, "Update failed. Response code: $responseCode, message: ${connection.responseMessage}")
            val errorStream = connection.errorStream?.bufferedReader()?.readText()
            Log.e(TAG, "Error body: $errorStream")
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
            is UploadRequestPlan.Json -> {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { os ->
                    val input = plan.payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
            }
            is UploadRequestPlan.Multipart -> {
                val metadata = plan.payload
                val file = plan.file
                val boundary = "Boundary-${System.currentTimeMillis()}"
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                DataOutputStream(connection.outputStream).use { dos ->
                    val lineEnd = "\r\n"
                    val twoHyphens = "--"

                    // Part 1: JSON metadata
                    dos.writeBytes(twoHyphens + boundary + lineEnd)
                    dos.writeBytes("Content-Disposition: form-data; name=\"json\"$lineEnd")
                    dos.writeBytes("Content-Type: application/json; charset=UTF-8$lineEnd")
                    dos.writeBytes(lineEnd)
                    dos.write(metadata.toString().toByteArray(Charsets.UTF_8))
                    dos.writeBytes(lineEnd)

                    // Part 2: File
                    dos.writeBytes(twoHyphens + boundary + lineEnd)
                    dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"" + lineEnd)
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
            }
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
            val errorStream = connection.errorStream?.bufferedReader()?.readText()
            Log.e(TAG, "Error body: $errorStream")
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
            is UploadRequestPlan.Json -> {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { os ->
                    val input = plan.payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
            }
            is UploadRequestPlan.Multipart -> {
                val file = plan.file
                val jsonPayload = plan.payload
                val boundary = "Boundary-${System.currentTimeMillis()}"
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                DataOutputStream(connection.outputStream).use { dos ->
                    val lineEnd = "\r\n"
                    val twoHyphens = "--"

                    // Part 1: JSON metadata
                    dos.writeBytes(twoHyphens + boundary + lineEnd)
                    dos.writeBytes("Content-Disposition: form-data; name=\"json\"$lineEnd")
                    dos.writeBytes("Content-Type: application/json; charset=UTF-8$lineEnd")
                    dos.writeBytes(lineEnd)
                    dos.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
                    dos.writeBytes(lineEnd)

                    // Part 2: File
                    dos.writeBytes(twoHyphens + boundary + lineEnd)
                    dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"" + lineEnd)
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

                    // End of multipart
                    dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                }
            }
            null -> {
                Log.e(TAG, "File not found for upload: $filePath")
                return null
            }
        }

        val responseCode = connection.responseCode
        if (isSuccessfulResponse(responseCode)) {
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(responseBody)
            jsonResponse.opt("response")
        } else {
            Log.e(TAG, "Recognition post failed. Response code: $responseCode, message: ${connection.responseMessage}")
            val errorStream = connection.errorStream?.bufferedReader()?.readText()
            Log.e(TAG, "Error body: $errorStream")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Exception during recognition post", e)
        null
    } as JSONObject?
}
