package tw.com.johnnyhng.eztalk.asr.utils

import android.content.ContextWrapper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript

class RecognitionUtilsOrchestrationTest {
    private val unusedContext = ContextWrapper(null)

    @Test
    fun mergeRemoteCandidatesIntoUtteranceVariantsAppendsDistinctRemoteCandidatesWhenEnabled() {
        val result = mergeRemoteCandidatesIntoUtteranceVariants(
            utteranceVariants = listOf("local-1", "shared"),
            remoteCandidates = listOf("remote-1", "shared", " "),
            enabled = true
        )

        assertEquals(listOf("local-1", "shared", "remote-1"), result)
    }

    @Test
    fun mergeRemoteCandidatesIntoUtteranceVariantsKeepsLocalVariantsWhenDisabled() {
        val result = mergeRemoteCandidatesIntoUtteranceVariants(
            utteranceVariants = listOf("local-1"),
            remoteCandidates = listOf("remote-1"),
            enabled = false
        )

        assertEquals(listOf("local-1"), result)
    }

    @Test
    fun resolveRemoteCandidatesReturnsCachedCandidatesWithoutCallingNetworkOrSave() {
        var postCalls = 0
        var savedMetadata: RemoteCandidateMetadata? = null

        val result = resolveRemoteCandidates(
            jsonlPath = "/tmp/sample.jsonl",
            wavFilePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            recognitionUrl = "https://example.com/process_audio",
            allowInsecureTls = false,
            originalText = "original",
            currentText = "current",
            readJsonlBlock = {
                JSONObject().apply {
                    put(
                        "remote_candidates",
                        JSONArray().apply {
                            put("cached-1")
                            put("cached-2")
                        }
                    )
                }
            },
            postRecognitionBlock = { _, _, _, _ ->
                postCalls += 1
                null
            },
            saveJsonlBlock = { savedMetadata = it }
        )

        assertEquals(listOf("cached-1", "cached-2"), result)
        assertEquals(0, postCalls)
        assertNull(savedMetadata)
    }

    @Test
    fun resolveRemoteCandidatesReturnsEmptyWhenRecognitionUrlIsBlank() {
        var postCalls = 0

        val result = resolveRemoteCandidates(
            jsonlPath = "/tmp/sample.jsonl",
            wavFilePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            recognitionUrl = "",
            allowInsecureTls = false,
            originalText = "original",
            currentText = "current",
            readJsonlBlock = { null },
            postRecognitionBlock = { _, _, _, _ ->
                postCalls += 1
                null
            },
            saveJsonlBlock = { error("save should not run") }
        )

        assertEquals(emptyList<String>(), result)
        assertEquals(0, postCalls)
    }

    @Test
    fun resolveRemoteCandidatesReturnsEmptyWhenRecognitionResponseIsNull() {
        var postCalls = 0

        val result = resolveRemoteCandidates(
            jsonlPath = "/tmp/sample.jsonl",
            wavFilePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            recognitionUrl = "https://example.com/process_audio",
            allowInsecureTls = false,
            originalText = "original",
            currentText = "current",
            readJsonlBlock = { null },
            postRecognitionBlock = { _, _, _, _ ->
                postCalls += 1
                null
            },
            saveJsonlBlock = { error("save should not run") }
        )

        assertEquals(emptyList<String>(), result)
        assertEquals(1, postCalls)
    }

    @Test
    fun resolveRemoteCandidatesSavesLatestMetadataAndReturnsFetchedCandidates() {
        var readCount = 0
        var savedMetadata: RemoteCandidateMetadata? = null

        val result = resolveRemoteCandidates(
            jsonlPath = "/tmp/sample.jsonl",
            wavFilePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            recognitionUrl = "https://example.com/process_audio",
            allowInsecureTls = false,
            originalText = "fallback-original",
            currentText = "fallback-current",
            readJsonlBlock = {
                readCount += 1
                when (readCount) {
                    1 -> JSONObject().apply {
                        put("modified", "stale-current")
                    }
                    else -> JSONObject().apply {
                        put("original", "latest-original")
                        put("modified", "latest-modified")
                        put("checked", true)
                        put("mutable", false)
                        put("removable", true)
                        put(
                            "local_candidates",
                            JSONArray().apply {
                                put("local-1")
                            }
                        )
                    }
                }
            },
            postRecognitionBlock = { _, _, _, _ ->
                JSONObject().apply {
                    put(
                        "sentence_candidates",
                        JSONArray().apply {
                            put("remote-1")
                            put("remote-2")
                        }
                    )
                }
            },
            saveJsonlBlock = { savedMetadata = it }
        )

        assertEquals(listOf("remote-1", "remote-2"), result)
        assertEquals(2, readCount)
        assertEquals("latest-original", savedMetadata?.originalText)
        assertEquals("latest-modified", savedMetadata?.modifiedText)
        assertEquals(true, savedMetadata?.checked)
        assertEquals(false, savedMetadata?.mutable)
        assertEquals(true, savedMetadata?.removable)
        assertEquals(listOf("local-1"), savedMetadata?.localCandidates)
        assertEquals(listOf("remote-1", "remote-2"), savedMetadata?.remoteCandidates)
    }

    @Test
    fun resolveRemoteCandidatesReturnsEmptyWhenParsedCandidatesAreEmpty() {
        var savedMetadata: RemoteCandidateMetadata? = null

        val result = resolveRemoteCandidates(
            jsonlPath = "/tmp/sample.jsonl",
            wavFilePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            recognitionUrl = "https://example.com/process_audio",
            allowInsecureTls = false,
            originalText = "original",
            currentText = "current",
            readJsonlBlock = { null },
            postRecognitionBlock = { _, _, _, _ ->
                JSONObject().apply {
                    put(
                        "sentence_candidates",
                        JSONArray().apply {
                            put("")
                            put(" ")
                        }
                    )
                }
            },
            saveJsonlBlock = { savedMetadata = it }
        )

        assertEquals(emptyList<String>(), result)
        assertNull(savedMetadata)
    }

    @Test
    fun loadTranslateCandidatesCombinesLocalUpdateAndFetchedRemoteCandidates() = runBlocking {
        val transcript = Transcript(
            recognizedText = "orig",
            modifiedText = "orig",
            wavFilePath = "/tmp/sample.wav"
        )

        val result = loadTranslateCandidates(
            context = unusedContext,
            userId = "tester@example.com",
            transcript = transcript,
            recognitionUrl = "https://example.com/process_audio",
            allowInsecureTls = true,
            audioReader = { floatArrayOf(1f, 2f, 3f) },
            recognizerBlock = { "local-rerun" },
            localTranscriptBlock = {
                transcript.copy(localCandidates = listOf("local-rerun"))
            },
            readJsonlBlock = { null },
            remoteCandidatesBlock = { _, _, _, _, _, _ -> listOf("remote-1", "remote-2") }
        )

        assertEquals(listOf("local-rerun"), result.transcript.localCandidates)
        assertEquals("local-rerun", result.localCandidate)
        assertEquals(listOf("remote-1", "remote-2"), result.remoteCandidates)
        assertEquals(listOf("remote-1", "remote-2"), result.transcript.remoteCandidates)
    }

    @Test
    fun loadTranslateCandidatesMergesRemoteCandidatesIntoUtteranceVariantsWhenEnabled() = runBlocking {
        val transcript = Transcript(
            recognizedText = "orig",
            modifiedText = "orig",
            wavFilePath = "/tmp/sample.wav",
            utteranceVariants = listOf("local-variant")
        )

        val result = loadTranslateCandidates(
            context = unusedContext,
            userId = "tester@example.com",
            transcript = transcript,
            recognitionUrl = "https://example.com/process_audio",
            allowInsecureTls = true,
            audioReader = { floatArrayOf(1f, 2f, 3f) },
            recognizerBlock = { "local-rerun" },
            includeRemoteCandidatesInUtteranceVariants = true,
            localTranscriptBlock = {
                transcript.copy(localCandidates = listOf("local-rerun"))
            },
            readJsonlBlock = { null },
            remoteCandidatesBlock = { _, _, _, _, _, _ -> listOf("remote-1", "local-variant") }
        )

        assertEquals(listOf("remote-1", "local-variant"), result.remoteCandidates)
        assertEquals(listOf("remote-1", "local-variant"), result.transcript.remoteCandidates)
        assertEquals(listOf("local-variant", "remote-1"), result.transcript.utteranceVariants)
    }

    @Test
    fun loadTranslateCandidatesPrefersJsonlSyncedCandidatesOverFetchedValues() = runBlocking {
        val transcript = Transcript(
            recognizedText = "orig",
            modifiedText = "orig",
            wavFilePath = "/tmp/sample.wav",
            localCandidates = listOf("existing-local")
        )

        val result = loadTranslateCandidates(
            context = unusedContext,
            userId = "tester@example.com",
            transcript = transcript,
            recognitionUrl = "https://example.com/process_audio",
            allowInsecureTls = false,
            audioReader = { error("audioReader should not run") },
            recognizerBlock = { error("recognizerBlock should not run") },
            localTranscriptBlock = { error("localTranscriptBlock should not run") },
            readJsonlBlock = {
                JSONObject().apply {
                    put(
                        "remote_candidates",
                        JSONArray().apply {
                            put("jsonl-remote")
                        }
                    )
                }
            },
            remoteCandidatesBlock = { _, _, _, _, _, _ -> listOf("fetched-remote") }
        )

        assertEquals(listOf("existing-local"), result.transcript.localCandidates)
        assertEquals(listOf("jsonl-remote"), result.remoteCandidates)
        assertEquals(listOf("jsonl-remote"), result.transcript.remoteCandidates)
        assertTrue(result.localCandidate == "existing-local")
    }
}
