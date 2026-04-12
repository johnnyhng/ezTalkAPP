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

internal fun parseRemoteCandidates(response: JSONObject?): List<String> {
    val candidates = response?.optJSONArray("sentence_candidates") ?: return emptyList()
    return List(candidates.length()) { index -> candidates.optString(index) }.filter { it.isNotBlank() }
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

internal fun buildRemoteCandidateWriteback(
    latestJsonlData: JSONObject?,
    fallbackOriginalText: String,
    fallbackCurrentText: String,
    response: JSONObject?
): RemoteCandidateMetadata? {
    val remoteCandidates = parseRemoteCandidates(response)
    if (remoteCandidates.isEmpty()) return null

    return buildRemoteCandidateMetadata(
        latestJsonlData = latestJsonlData,
        fallbackOriginalText = fallbackOriginalText,
        fallbackCurrentText = fallbackCurrentText,
        remoteCandidates = remoteCandidates
    )
}

internal fun resolveRemoteCandidates(
    jsonlPath: String,
    wavFilePath: String,
    userId: String,
    recognitionUrl: String,
    allowInsecureTls: Boolean,
    originalText: String,
    currentText: String,
    readJsonlBlock: (String) -> JSONObject?,
    postRecognitionBlock: (String, String, String, Boolean) -> JSONObject?,
    saveJsonlBlock: (RemoteCandidateMetadata) -> Unit
): List<String> {
    val jsonlData = readJsonlBlock(jsonlPath)
    val existingCandidates = readCachedRemoteCandidates(jsonlData)

    if (existingCandidates.isNotEmpty()) {
        return existingCandidates
    }

    if (recognitionUrl.isBlank()) {
        return emptyList()
    }

    val response = postRecognitionBlock(recognitionUrl, wavFilePath, userId, allowInsecureTls) ?: return emptyList()

    return try {
        val latestJsonlData = readJsonlBlock(jsonlPath)
        val metadata = buildRemoteCandidateWriteback(
            latestJsonlData = latestJsonlData,
            fallbackOriginalText = originalText,
            fallbackCurrentText = currentText,
            response = response
        ) ?: return emptyList()

        saveJsonlBlock(metadata)
        metadata.remoteCandidates
    } catch (e: Exception) {
        emptyList()
    }
}

suspend fun getRemoteCandidates(
    context: Context,
    wavFilePath: String,
    userId: String,
    recognitionUrl: String,
    allowInsecureTls: Boolean = false,
    originalText: String,
    currentText: String
): List<String> {
    Log.d(
        TAG,
        "getRemoteCandidates: allowInsecureTls=$allowInsecureTls, recognitionUrl=$recognitionUrl"
    )
    val jsonlFile = File(wavFilePath).resolveSibling(
        File(wavFilePath).nameWithoutExtension + ".jsonl"
    )
    val filename = File(wavFilePath).nameWithoutExtension

    return withContext(Dispatchers.IO) {
        resolveRemoteCandidates(
            jsonlPath = jsonlFile.absolutePath,
            wavFilePath = wavFilePath,
            userId = userId,
            recognitionUrl = recognitionUrl,
            allowInsecureTls = allowInsecureTls,
            originalText = originalText,
            currentText = currentText,
            readJsonlBlock = ::readJsonl,
            postRecognitionBlock = { url, path, id, insecureTls ->
                postForRecognition(
                    recognitionUrl = url,
                    filePath = path,
                    userId = id,
                    allowInsecureTls = insecureTls
                )
            },
            saveJsonlBlock = { metadata ->
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
            }
        ).also { candidates ->
            if (candidates.isNotEmpty()) {
                Log.d(TAG, "Resolved ${candidates.size} remote candidates")
            }
        }
    }
}
