package com.k2fsa.sherpa.onnx.simulate.streaming.asr.utils

import android.util.Log
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection


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

private fun packageUploadJsonMetadata(path: String, userId: String): JSONObject? {
    try {
        val wavFile = File(path)
        val jsonlPath = path.substringBeforeLast(".") + ".jsonl"
        val jsonlFile = File(jsonlPath)
        var label = ""
        if (jsonlFile.exists()) {
            try {
                val jsonlContent = jsonlFile.readText()
                if (jsonlContent.isNotBlank()) {
                    val jsonObject = JSONObject(jsonlContent)
                    label = jsonObject.optString("modified", "")
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

        return json
    } catch (e: Exception) {
        Log.e(TAG, "Error packaging upload JSON metadata for $path", e)
        return null
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

fun postFeedback(
    feedbackUrl: String,
    filePath: String,
    userId: String,
    sendFileByJson: Boolean = true
): Boolean {
    return try {
        val url = URL(feedbackUrl)
        val connection = url.openConnection() as HttpURLConnection
        val hostnameVerifier = HostnameVerifier { _, _ -> true }
        (connection as? HttpsURLConnection)?.hostnameVerifier = hostnameVerifier

        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 5000 // 5 seconds
        connection.readTimeout = 5000 // 5 seconds

        if (sendFileByJson) {
            val jsonPayload = packageUploadJson(filePath, userId)
            if (jsonPayload == null) {
                Log.e(TAG, "Failed to create JSON payload for $filePath")
                return false
            }

            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { os ->
                val input = jsonPayload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
        } else {
            val metadata = packageUploadJsonMetadata(filePath, userId)
            if (metadata == null) {
                Log.e(TAG, "Failed to create metadata for $filePath")
                return false
            }
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found for upload: $filePath")
                return false
            }

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

                // End of multipart
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
            }
        }

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            true
        } else {
            Log.e(TAG, "Feedback post failed. Response code: $responseCode, message: ${connection.responseMessage}")
            val errorStream = connection.errorStream?.bufferedReader()?.readText()
            Log.e(TAG, "Error body: $errorStream")
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, "Exception during feedback post", e)
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

        val jsonPayload = JSONObject()
        jsonPayload.put("login_user", userId.split("@")[0])
        jsonPayload.put("filename", filePath.substringAfterLast("/"))
        jsonPayload.put("label", "tmp")
        jsonPayload.put("num_of_stn", 8)


        if (sendFileByJson) {
            jsonPayload.put("raw", readWavFileToJsonArray(filePath))

            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { os ->
                val input = jsonPayload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
        } else {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found for upload: $filePath")
                return null
            }

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

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
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
