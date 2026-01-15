package tw.com.johnnyhng.eztalk.asr.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import tw.com.johnnyhng.eztalk.asr.data.classes.QueueState
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Computes the SHA-256 hash of a string.
 *
 * @param input The string to hash.
 * @return The SHA-256 hash as a hexadecimal string.
 */
fun sha256(input: String): String {
    val bytes = input.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.fold("") { str, it -> str + "%02x".format(it) }
}

/**
 * Computes the SHA-256 hash of a file.
 *
 * @param file The file to hash.
 * @return The SHA-256 hash as a hexadecimal string, or null on error.
 */
fun sha256(file: File): String? {
    return try {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(1024)
            var a: Int
            while (fis.read(buffer).also { a = it } != -1) {
                md.update(buffer, 0, a)
            }
        }
        val digest = md.digest()
        digest.fold("") { str, it -> str + "%02x".format(it) }
    } catch (e: Exception) {
        null
    }
}

fun saveQueueState(context: Context, userId: String, state: QueueState) {
    val dir = File(context.filesDir, "data/$userId")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    val file = File(dir, "seq.jsonl")
    try {
        val jsonObject = JSONObject()
        jsonObject.put("currentText", state.currentText)
        val jsonArray = JSONArray(state.queue)
        jsonObject.put("queue", jsonArray)
        file.writeText(jsonObject.toString())
    } catch (e: Exception) {
        // Handle exception
    }
}

fun restoreQueueState(context: Context, userId: String): QueueState? {
    val file = File(context.filesDir, "data/$userId/seq.jsonl")
    if (!file.exists()) {
        return null
    }
    return try {
        val text = file.readText()
        val jsonObject = JSONObject(text)
        val currentText = jsonObject.getString("currentText")
        val queueArray = jsonObject.getJSONArray("queue")
        val queue = mutableListOf<String>()
        for (i in 0 until queueArray.length()) {
            queue.add(queueArray.getString(i))
        }
        QueueState(currentText, queue)
    } catch (e: Exception) {
        null
    }
}

fun deleteQueueState(context: Context, userId: String) {
    val file = File(context.filesDir, "data/$userId/seq.jsonl")
    if (file.exists()) {
        file.delete()
    }
}
