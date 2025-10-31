package tw.com.johnnyhng.eztalk.asr.utils

import android.content.Context
import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun getRemoteCandidates(
    context: Context,
    wavFilePath: String,
    userId: String,
    recognitionUrl: String,
    originalText: String,
    currentText: String
): List<String> {
    val jsonlFile = File(wavFilePath).resolveSibling(
        File(wavFilePath).nameWithoutExtension + ".jsonl"
    )

    return withContext(Dispatchers.IO) {
        val jsonlData = readJsonl(jsonlFile.absolutePath)
        val existingCandidates = jsonlData?.optJSONArray("remote_candidates")

        if (existingCandidates != null && existingCandidates.length() > 0) {
            Log.d(TAG, "Found existing remote candidates in jsonl file")
            val sentences = mutableListOf<String>()
            for (i in 0 until existingCandidates.length()) {
                sentences.add(existingCandidates.getString(i))
            }
            return@withContext sentences
        }

        if (recognitionUrl.isNotBlank()) {
            val response = postForRecognition(recognitionUrl, wavFilePath, userId)
            if (response != null) {
                try {
                    val candidates = response.getJSONArray("sentence_candidates")
                    val sentences = mutableListOf<String>()
                    for (i in 0 until candidates.length()) {
                        sentences.add(candidates.getString(i))
                    }

                    // Re-read the jsonl file to get the most up-to-date user edits before writing.
                    val latestJsonlData = readJsonl(jsonlFile.absolutePath)

                    // Save to jsonl
                    val file = File(wavFilePath)
                    val filename = file.nameWithoutExtension
                    val original =
                        latestJsonlData?.optString("original", originalText) ?: originalText
                    val modified =
                        latestJsonlData?.optString("modified", currentText) ?: currentText
                    val checked = latestJsonlData?.optBoolean("checked", false) ?: false

                    saveJsonl(
                        context = context,
                        userId = userId,
                        filename = filename,
                        originalText = original,
                        modifiedText = modified,
                        checked = checked,
                        remoteCandidates = sentences
                    )
                    return@withContext sentences
                } catch (e: Exception) {
                    Log.e(TAG, "Could not parse remote recognition result", e)
                }
            }
        }
        return@withContext emptyList()
    }
}
