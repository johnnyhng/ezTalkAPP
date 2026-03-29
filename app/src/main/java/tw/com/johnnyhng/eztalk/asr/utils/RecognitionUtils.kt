package tw.com.johnnyhng.eztalk.asr.utils

import android.content.Context
import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

internal data class RemoteCandidateMetadata(
    val originalText: String,
    val modifiedText: String,
    val checked: Boolean,
    val mutable: Boolean,
    val removable: Boolean,
    val localCandidates: List<String>,
    val remoteCandidates: List<String>
)

internal fun readCachedRemoteCandidates(jsonlData: JSONObject?): List<String> {
    return jsonlData?.optStringList("remote_candidates").orEmpty()
}

internal fun buildRemoteCandidateMetadata(
    latestJsonlData: JSONObject?,
    fallbackOriginalText: String,
    fallbackCurrentText: String,
    remoteCandidates: List<String>
): RemoteCandidateMetadata {
    val originalText =
        latestJsonlData?.optString("original", fallbackOriginalText) ?: fallbackOriginalText
    val modifiedText =
        latestJsonlData?.optString("modified", fallbackCurrentText) ?: fallbackCurrentText
    val checked = latestJsonlData?.optBoolean("checked", false) ?: false
    val mutable = latestJsonlData?.optBoolean(
        "mutable",
        latestJsonlData.optBoolean("canCheck", true)
    ) ?: true
    val removable = latestJsonlData?.optBoolean("removable", false) ?: false
    val localCandidates = latestJsonlData?.optStringList("local_candidates").orEmpty()

    return RemoteCandidateMetadata(
        originalText = originalText,
        modifiedText = modifiedText,
        checked = checked,
        mutable = mutable,
        removable = removable,
        localCandidates = localCandidates,
        remoteCandidates = remoteCandidates
    )
}

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
        val existingCandidates = readCachedRemoteCandidates(jsonlData)

        if (existingCandidates.isNotEmpty()) {
            Log.d(TAG, "Found existing remote candidates in jsonl file")
            return@withContext existingCandidates
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
                    val metadata = buildRemoteCandidateMetadata(
                        latestJsonlData = latestJsonlData,
                        fallbackOriginalText = originalText,
                        fallbackCurrentText = currentText,
                        remoteCandidates = sentences
                    )

                    // Save to jsonl
                    val file = File(wavFilePath)
                    val filename = file.nameWithoutExtension

                    saveJsonl(
                        context = context,
                        userId = userId,
                        filename = filename,
                        originalText = metadata.originalText,
                        modifiedText = metadata.modifiedText,
                        checked = metadata.checked,
                        mutable = metadata.mutable,
                        removable = metadata.removable,
                        localCandidates = metadata.localCandidates,
                        remoteCandidates = metadata.remoteCandidates
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
