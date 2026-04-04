package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures
import java.io.File

class ApiExecutionFlowTest {
    @Test
    fun buildUploadMetadataSourceBuildsSiblingJsonlPath() {
        val source = buildUploadMetadataSource("/tmp/audio/sample.wav")

        assertEquals("/tmp/audio/sample.wav", source.wavFile.path)
        assertEquals("/tmp/audio/sample.jsonl", source.jsonlFile.path)
    }

    @Test
    fun readUploadMetadataSnapshotReturnsEmptySnapshotWhenFileIsBlank() {
        val blankFile = File(TestFixtures.tempDir("api-snapshot"), "blank.jsonl").apply {
            writeText("")
        }
        val blank = readUploadMetadataSnapshot(
            jsonlFile = blankFile,
            jsonlReader = { it.readText() }
        )

        assertEquals("", blank.label)
        assertEquals(0, blank.remoteCandidates.length())
    }

    @Test
    fun packageUploadJsonMetadataBuildsMetadataFromJsonlSnapshot() {
        val dir = TestFixtures.tempDir("package-metadata")
        val wav = File(dir, "sample.wav").apply { writeBytes(ByteArray(44)) }
        val jsonl = File(dir, "sample.jsonl").apply {
            writeText(
                JSONObject()
                    .put("modified", "confirmed text")
                    .put("remote_candidates", JSONArray().put("r1").put("r2"))
                    .toString()
            )
        }

        val metadata = packageUploadJsonMetadata(wav.absolutePath, "tester@example.com")

        assertNotNull(metadata)
        assertEquals("sample.wav", metadata?.getString("filename"))
        assertEquals("confirmed text", metadata?.getString("label"))
        assertEquals(2, metadata?.getJSONArray("remote_candidates")?.length())
        assertTrue(jsonl.exists())
    }

    @Test
    fun packageUploadJsonUsesInjectedLoadersAndCombinesMetadataWithRaw() {
        val packaged = packageUploadJson(
            path = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadataLoader = { _, _ -> JSONObject().put("label", "confirmed") },
            rawLoader = { JSONArray().put(1).put(2) }
        )

        assertNotNull(packaged)
        assertEquals("confirmed", packaged?.getString("label"))
        assertEquals(2, packaged?.getJSONArray("raw")?.length())
    }

    @Test
    fun buildFeedbackExecutionReadsJsonlAndPicksUpdatesRoute() {
        val execution = buildFeedbackExecution(
            backendUrl = "https://backend.example.com",
            filePath = "/tmp/sample.wav",
            metadataReader = {
                assertEquals("/tmp/sample.jsonl", it)
                JSONObject().put("remote_candidates", JSONArray().put("remote"))
            }
        )

        assertEquals("/tmp/sample.jsonl", execution.jsonlPath)
        assertEquals(FeedbackRoute.PUT_UPDATES, execution.dispatchPlan.route)
        assertEquals("https://backend.example.com/updates", execution.dispatchPlan.endpoint)
        assertEquals(1, execution.metadata?.getJSONArray("remote_candidates")?.length())
    }

    @Test
    fun dispatchFeedbackExecutionDelegatesToSelectedBlock() {
        val execution = FeedbackExecution(
            jsonlPath = "/tmp/sample.jsonl",
            metadata = JSONObject().put("local_candidates", JSONArray().put("local")),
            dispatchPlan = FeedbackDispatchPlan(
                route = FeedbackRoute.POST_PROCESS_AUDIO,
                endpoint = "https://recognition.example.com/process_audio"
            )
        )

        var invoked = ""
        val success = dispatchFeedbackExecution(
            execution = execution,
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            putUpdates = { _, _, _, _ ->
                invoked = "put"
                false
            },
            postProcessAudioBlock = { endpoint, path, userId, metadata ->
                invoked = "process:$endpoint:$path:$userId:${metadata?.getJSONArray("local_candidates")?.length()}"
                true
            },
            postTransferBlock = { _, _, _ ->
                invoked = "transfer"
                false
            }
        )

        assertTrue(success)
        assertEquals(
            "process:https://recognition.example.com/process_audio:/tmp/sample.wav:tester@example.com:1",
            invoked
        )
    }

    @Test
    fun buildAndDispatchFeedbackExecutionUsesTransferWhenMetadataIsMissing() {
        var transferCalls = 0

        val execution = buildFeedbackExecution(
            backendUrl = "https://backend.example.com",
            filePath = "/tmp/sample.wav",
            metadataReader = { null }
        )
        val success = dispatchFeedbackExecution(
            execution = execution,
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            putUpdates = { _, _, _, _ -> false },
            postProcessAudioBlock = { _, _, _, _ -> false },
            postTransferBlock = { endpoint, path, userId ->
                transferCalls += 1
                endpoint == "https://backend.example.com/transfer" &&
                    path == "/tmp/sample.wav" &&
                    userId == "tester@example.com"
            }
        )

        assertTrue(success)
        assertEquals(1, transferCalls)
    }

    @Test
    fun buildAndDispatchFeedbackExecutionUsesProcessAudioForLocalOnlyMetadata() {
        var processCalls = 0

        val execution = buildFeedbackExecution(
            backendUrl = "https://backend.example.com",
            filePath = "/tmp/sample.wav",
            metadataReader = {
                JSONObject().put("local_candidates", JSONArray().put("local"))
            }
        )
        val success = dispatchFeedbackExecution(
            execution = execution,
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            putUpdates = { _, _, _, _ -> false },
            postProcessAudioBlock = { endpoint, path, userId, metadata ->
                processCalls += 1
                endpoint == "https://backend.example.com/process_audio" &&
                    path == "/tmp/sample.wav" &&
                    userId == "tester@example.com" &&
                    metadata?.getJSONArray("local_candidates")?.length() == 1
            },
            postTransferBlock = { _, _, _ -> false }
        )

        assertTrue(success)
        assertEquals(1, processCalls)
    }

    @Test
    fun buildAndDispatchFeedbackExecutionUsesUpdatesForRemoteMetadata() {
        var updateCalls = 0

        val execution = buildFeedbackExecution(
            backendUrl = "https://backend.example.com",
            filePath = "/tmp/sample.wav",
            metadataReader = {
                JSONObject().put("remote_candidates", JSONArray().put("remote"))
            }
        )
        val success = dispatchFeedbackExecution(
            execution = execution,
            filePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            putUpdates = { endpoint, path, userId, metadata ->
                updateCalls += 1
                endpoint == "https://backend.example.com/updates" &&
                    path == "/tmp/sample.wav" &&
                    userId == "tester@example.com" &&
                    metadata?.getJSONArray("remote_candidates")?.length() == 1
            },
            postProcessAudioBlock = { _, _, _, _ -> false },
            postTransferBlock = { _, _, _ -> false }
        )

        assertTrue(success)
        assertEquals(1, updateCalls)
    }

    @Test
    fun combineUploadJsonReturnsNullWhenRawLoaderFailsThroughPackageUploadJson() {
        val packaged = packageUploadJson(
            path = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadataLoader = { _, _ -> JSONObject().put("label", "confirmed") },
            rawLoader = { null }
        )

        assertNull(packaged)
    }
}
