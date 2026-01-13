package tw.com.johnnyhng.eztalk.asr.utils

import android.content.Context
import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists


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
 * @param remoteCandidates Optional list of remote candidates.
 * @return The absolute path to the saved JSONL file, or null on failure.
 */
fun saveJsonl(
    context: Context,
    userId: String,
    filename: String,
    originalText: String,
    modifiedText: String,
    checked: Boolean,
    remoteCandidates: List<String>? = null
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
            remoteCandidates?.let {
                put("remote_candidates", JSONArray(it))
            }
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

/**
 * Reads a JSONL file and returns its content as a JSONObject.
 *
 * @param path The absolute path to the JSONL file.
 * @return A JSONObject containing the file's content, or null on error or if the file doesn't exist.
 */
fun readJsonl(path: String): JSONObject? {
    try {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            return null
        }
        val text = file.readText()
        return if (text.isNotBlank()) JSONObject(text) else null
    } catch (e: Exception) {
        Log.e(TAG, "Error reading or parsing JSONL file: $path", e)
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
 * Deletes a WAV file and its corresponding JSONL file given the WAV file path.
 *
 * @param wavFilePath The absolute path to the WAV file.
 * @return True if both files were deleted successfully, false otherwise.
 */
fun deleteTranscriptFiles(wavFilePath: String): Boolean {
    var allDeleted = true
    val wavFile = File(wavFilePath)
    if (wavFile.exists()) {
        try {
            if (wavFile.delete()) {
                Log.i(TAG, "Successfully deleted WAV file: $wavFilePath")
            } else {
                Log.e(TAG, "Failed to delete WAV file: $wavFilePath")
                allDeleted = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting WAV file: $wavFilePath", e)
            allDeleted = false
        }
    } else {
        Log.w(TAG, "WAV file does not exist: $wavFilePath")
    }

    val jsonlFilePath = wavFilePath.replace(".wav", ".jsonl")
    val jsonlFile = File(jsonlFilePath)
    if (jsonlFile.exists()) {
        try {
            if (jsonlFile.delete()) {
                Log.i(TAG, "Successfully deleted JSONL file: $jsonlFilePath")
            } else {
                Log.e(TAG, "Failed to delete JSONL file: $jsonlFilePath")
                allDeleted = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting JSONL file: $jsonlFilePath", e)
            allDeleted = false
        }
    } else {
        Log.w(TAG, "JSONL file does not exist: $jsonlFilePath")
    }
    return allDeleted
}