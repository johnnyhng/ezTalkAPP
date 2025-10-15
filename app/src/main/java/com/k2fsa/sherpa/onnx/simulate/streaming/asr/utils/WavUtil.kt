package com.k2fsa.sherpa.onnx.simulate.streaming.asr.utils

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession


/**
 * Saves a FloatArray of audio samples to a WAV file in a structured directory.
 * The path will be: files/wavs/<userId>/<filename>.wav
 *
 * @param context The application context to get the files directory.
 * @param samples The FloatArray of audio samples (normalized between -1.0 and 1.0).
 * @param sampleRate The sample rate of the audio (e.g., 16000).
 * @param numChannels The number of channels (1 for mono, 2 for stereo).
 * @param userId The ID of the user.
 * @param filename The desired name for the WAV file (without the .wav extension).
 * @return The absolute path to the saved WAV file, or null on failure.
 */
fun saveAsWav(
    context: Context,
    samples: FloatArray,
    sampleRate: Int,
    numChannels: Int,
    userId: String,
    filename: String
): String? {
    // Directory path is now just based on the user ID
    val dir = File(context.filesDir, "wavs/$userId")

    if (!dir.exists()) {
        if (!dir.mkdirs()) {
            Log.e(TAG, "Failed to create directory: ${dir.absolutePath}")
            return null
        }
    }

    val file = File(dir, "$filename.wav")
    try {
        FileOutputStream(file).use { out ->
            // Convert float samples to 16-bit PCM byte array
            val byteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                val shortSample = (sample * 32767.0f)
                    .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
                    .toInt().toShort()
                byteBuffer.putShort(shortSample)
            }
            val pcmData = byteBuffer.array()

            // Write WAV header
            writeWavHeader(out, pcmData.size, sampleRate, numChannels)
            // Write PCM data
            out.write(pcmData)
            Log.i(TAG, "Successfully saved WAV file to ${file.absolutePath}")
            return file.absolutePath
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error saving WAV file", e)
        return null
    }
}

/**
 * Saves or appends a JSON object to a .jsonl file.
 *
 * @param context The application context.
 * @param userId The ID of the user.
 * @param filename The base filename (e.g., "rec_20231027-123456").
 * @param originalText The original recognized text.
 * @param modifiedText The (potentially) modified text.
 * @param checked Whether the user has listened to the audio.
 * @return The absolute path to the saved JSONL file, or null on failure.
 */
fun saveJsonl(
    context: Context,
    userId: String,
    filename: String,
    originalText: String,
    modifiedText: String,
    checked: Boolean
): String? {
    val dir = File(context.filesDir, "wavs/$userId")
    if (!dir.exists()) {
        if (!dir.mkdirs()) {
            Log.e(TAG, "Failed to create directory for jsonl: ${dir.absolutePath}")
            return null
        }
    }
    val file = File(dir, "$filename.jsonl")
    try {
        val jsonObject = JSONObject().apply {
            put("original", originalText)
            put("modified", modifiedText)
            put("checked", checked)
        }
        val jsonLine = jsonObject.toString() + "\n"

        // Overwrite the file with the new JSON line
        file.writeText(jsonLine)

        Log.i(TAG, "Successfully appended to JSONL file: ${file.absolutePath}")
        return file.absolutePath
    } catch (e: Exception) {
        Log.e(TAG, "Error saving JSONL file", e)
        return null
    }
}


private fun writeWavHeader(
    out: FileOutputStream,
    pcmDataSize: Int,
    sampleRate: Int,
    numChannels: Int,
) {
    val header = ByteArray(44)
    val totalDataLen = pcmDataSize + 36
    val byteRate = sampleRate * numChannels * 2 // 16-bit PCM

    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    header[4] = (totalDataLen and 0xff).toByte()
    header[5] = (totalDataLen shr 8 and 0xff).toByte()
    header[6] = (totalDataLen shr 16 and 0xff).toByte()
    header[7] = (totalDataLen shr 24 and 0xff).toByte()
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    header[16] = 16
    header[17] = 0
    header[18] = 0
    header[19] = 0
    header[20] = 1
    header[21] = 0
    header[22] = numChannels.toByte()
    header[23] = 0
    header[24] = (sampleRate and 0xff).toByte()
    header[25] = (sampleRate shr 8 and 0xff).toByte()
    header[26] = (sampleRate shr 16 and 0xff).toByte()
    header[27] = (sampleRate shr 24 and 0xff).toByte()
    header[28] = (byteRate and 0xff).toByte()
    header[29] = (byteRate shr 8 and 0xff).toByte()
    header[30] = (byteRate shr 16 and 0xff).toByte()
    header[31] = (byteRate shr 24 and 0xff).toByte()
    header[32] = (numChannels * 2).toByte()
    header[33] = 0
    header[34] = 16
    header[35] = 0
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    header[40] = (pcmDataSize and 0xff).toByte()
    header[41] = (pcmDataSize shr 8 and 0xff).toByte()
    header[42] = (pcmDataSize shr 16 and 0xff).toByte()
    header[43] = (pcmDataSize shr 24 and 0xff).toByte()

    out.write(header, 0, 44)
}

/**
 * Reads a WAV file and returns its audio data as a FloatArray.
 * Note: This makes a simplifying assumption that the WAV file is 16-bit PCM.
 *
 * @param path The absolute path to the WAV file.
 * @return A FloatArray containing the audio samples normalized to [-1, 1], or null on error.
 */
internal fun readWavFileToFloatArray(path: String): FloatArray? {
    try {
        val file = File(path)
        val fileInputStream = FileInputStream(file)
        val byteBuffer = fileInputStream.readBytes()
        fileInputStream.close()

        // The first 44 bytes of a standard WAV file are the header. We skip it.
        val headerSize = 44
        if (byteBuffer.size <= headerSize) {
            Log.e(TAG, "WAV file is too small to contain audio data: ${file.name}")
            return null
        }

        // We assume the audio data is 16-bit PCM, little-endian.
        val shortBuffer = ByteBuffer.wrap(byteBuffer, headerSize, byteBuffer.size - headerSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val numSamples = shortBuffer.remaining()
        val floatArray = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            // Convert 16-bit short to float in the range [-1.0, 1.0]
            floatArray[i] = shortBuffer.get(i) / 32768.0f
        }
        return floatArray
    } catch (e: Exception) {
        Log.e(TAG, "Error reading WAV file: $path", e)
        return null
    }
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
