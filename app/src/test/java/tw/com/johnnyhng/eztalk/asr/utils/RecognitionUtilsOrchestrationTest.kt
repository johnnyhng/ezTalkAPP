package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognitionUtilsOrchestrationTest {
    @Test
    fun resolveRemoteCandidatesReturnsCachedCandidatesWithoutCallingNetworkOrSave() {
        var postCalls = 0
        var savedMetadata: RemoteCandidateMetadata? = null

        val result = resolveRemoteCandidates(
            jsonlPath = "/tmp/sample.jsonl",
            wavFilePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            recognitionUrl = "https://example.com/process_audio",
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
            postRecognitionBlock = { _, _, _ ->
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
            originalText = "original",
            currentText = "current",
            readJsonlBlock = { null },
            postRecognitionBlock = { _, _, _ ->
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
            originalText = "original",
            currentText = "current",
            readJsonlBlock = { null },
            postRecognitionBlock = { _, _, _ ->
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
            postRecognitionBlock = { _, _, _ ->
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
            originalText = "original",
            currentText = "current",
            readJsonlBlock = { null },
            postRecognitionBlock = { _, _, _ ->
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
}
