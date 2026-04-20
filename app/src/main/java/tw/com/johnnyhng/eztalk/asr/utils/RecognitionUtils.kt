package tw.com.johnnyhng.eztalk.asr.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import java.io.File

internal data class TranslateCandidateLoadResult(
    val transcript: Transcript,
    val localCandidate: String?,
    val remoteCandidates: List<String>
)

internal data class RemoteCandidateMetadata(
    val originalText: String,
    val modifiedText: String,
    val checked: Boolean,
    val mutable: Boolean,
    val removable: Boolean,
    val utteranceVariants: List<String>,
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
    val utteranceVariants = latestJsonlData?.optStringList("utterance_variants").orEmpty()
    val localCandidates = latestJsonlData?.optStringList("local_candidates").orEmpty()

    return RemoteCandidateMetadata(
        originalText = originalText,
        modifiedText = modifiedText,
        checked = checked,
        mutable = mutable,
        removable = removable,
        utteranceVariants = utteranceVariants,
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

internal fun persistTranscriptJsonl(
    context: Context,
    userId: String,
    transcript: Transcript
): String? {
    if (transcript.wavFilePath.isBlank()) return null
    return saveJsonl(
        context = context,
        userId = userId,
        filename = File(transcript.wavFilePath).nameWithoutExtension,
        originalText = transcript.recognizedText,
        modifiedText = transcript.modifiedText,
        checked = transcript.checked,
        mutable = transcript.mutable,
        removable = transcript.removable,
        utteranceVariants = transcript.utteranceVariants,
        localCandidates = transcript.localCandidates,
        remoteCandidates = transcript.remoteCandidates
    )
}

internal fun syncTranscriptCandidatesFromJsonl(
    transcript: Transcript,
    readJsonlBlock: (String) -> JSONObject? = ::readJsonl
): Transcript {
    if (transcript.wavFilePath.isBlank()) return transcript
    val json = readJsonlBlock(
        File(transcript.wavFilePath)
            .resolveSibling("${File(transcript.wavFilePath).nameWithoutExtension}.jsonl")
            .absolutePath
    ) ?: return transcript

    val utteranceVariantsFromJson = json.optStringList("utterance_variants")
    val localCandidatesFromJson = json.optStringList("local_candidates")
    val remoteCandidatesFromJson = json.optStringList("remote_candidates")
    if (utteranceVariantsFromJson.isEmpty() && localCandidatesFromJson.isEmpty() && remoteCandidatesFromJson.isEmpty()) {
        return transcript
    }

    return transcript.copy(
        utteranceVariants = if (utteranceVariantsFromJson.isNotEmpty()) {
            utteranceVariantsFromJson
        } else {
            transcript.utteranceVariants
        },
        localCandidates = if (localCandidatesFromJson.isNotEmpty()) {
            localCandidatesFromJson
        } else {
            transcript.localCandidates
        },
        remoteCandidates = if (remoteCandidatesFromJson.isNotEmpty()) {
            remoteCandidatesFromJson
        } else {
            transcript.remoteCandidates
        }
    )
}

internal suspend fun resolveLocalCandidateTranscript(
    context: Context,
    userId: String,
    transcript: Transcript,
    audioReader: (String) -> FloatArray?,
    recognizerBlock: (FloatArray) -> String
): Transcript? {
    if (transcript.wavFilePath.isBlank() || transcript.localCandidates.isNotEmpty()) return null

    val audioData = audioReader(transcript.wavFilePath) ?: return null
    val localResultText = recognizerBlock(audioData).trim()
    if (localResultText.isBlank()) return null

    val updatedTranscript = transcript.copy(localCandidates = listOf(localResultText))
    persistTranscriptJsonl(
        context = context,
        userId = userId,
        transcript = updatedTranscript
    )
    return updatedTranscript
}

internal suspend fun loadTranslateCandidates(
    context: Context,
    userId: String,
    transcript: Transcript,
    recognitionUrl: String,
    allowInsecureTls: Boolean,
    audioReader: (String) -> FloatArray?,
    recognizerBlock: (FloatArray) -> String,
    localTranscriptBlock: suspend (Transcript) -> Transcript? = { currentTranscript ->
        resolveLocalCandidateTranscript(
            context = context,
            userId = userId,
            transcript = currentTranscript,
            audioReader = audioReader,
            recognizerBlock = recognizerBlock
        )
    },
    readJsonlBlock: (String) -> JSONObject? = ::readJsonl,
    remoteCandidatesBlock: suspend (
        wavFilePath: String,
        userId: String,
        recognitionUrl: String,
        allowInsecureTls: Boolean,
        originalText: String,
        currentText: String
    ) -> List<String> = { wavFilePath, remoteUserId, remoteRecognitionUrl, insecureTls, originalText, currentText ->
        getRemoteCandidates(
            context = context,
            wavFilePath = wavFilePath,
            userId = remoteUserId,
            recognitionUrl = remoteRecognitionUrl,
            allowInsecureTls = insecureTls,
            originalText = originalText,
            currentText = currentText
        )
    }
): TranslateCandidateLoadResult = coroutineScope {
    val localDeferred = async(Dispatchers.IO) {
        if (transcript.localCandidates.isEmpty()) {
            localTranscriptBlock(transcript)
        } else {
            null
        }
    }
    val remoteDeferred = async(Dispatchers.IO) {
        if (recognitionUrl.isBlank()) {
            emptyList()
        } else {
            remoteCandidatesBlock(
                transcript.wavFilePath,
                userId,
                recognitionUrl,
                allowInsecureTls,
                transcript.recognizedText,
                transcript.modifiedText
            )
        }
    }

    val transcriptAfterLocal = localDeferred.await() ?: transcript
    val fetchedRemoteCandidates = remoteDeferred.await()
    val syncedTranscript = syncTranscriptCandidatesFromJsonl(
        transcript = transcriptAfterLocal,
        readJsonlBlock = readJsonlBlock
    )
    val finalRemoteCandidates = if (syncedTranscript.remoteCandidates.isNotEmpty()) {
        syncedTranscript.remoteCandidates
    } else {
        fetchedRemoteCandidates
    }
    val finalTranscript = if (finalRemoteCandidates != syncedTranscript.remoteCandidates) {
        syncedTranscript.copy(remoteCandidates = finalRemoteCandidates)
    } else {
        syncedTranscript
    }

    TranslateCandidateLoadResult(
        transcript = finalTranscript,
        localCandidate = finalTranscript.localCandidates.firstOrNull(),
        remoteCandidates = finalRemoteCandidates
    )
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
                    utteranceVariants = metadata.utteranceVariants,
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
