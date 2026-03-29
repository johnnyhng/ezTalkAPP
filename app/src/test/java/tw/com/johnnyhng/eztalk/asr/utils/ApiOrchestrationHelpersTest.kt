package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures
import java.io.File

class ApiOrchestrationHelpersTest {
    @Test
    fun readWavJsonArrayReturnsUnsignedByteValuesFromInjectedReader() {
        val wavFile = File("/tmp/sample.wav")

        val result = readWavJsonArray(wavFile) {
            ByteArray(46).also { bytes ->
                bytes[0] = 0
                bytes[44] = (-1).toByte()
                bytes[45] = 7
            }
        }

        assertNotNull(result)
        assertEquals(46, result?.length())
        assertEquals(255, result?.getInt(44))
        assertEquals(7, result?.getInt(45))
    }

    @Test
    fun readWavJsonArrayReturnsNullWhenReaderProvidesInvalidHeaderSizedBytes() {
        val result = readWavJsonArray(File("/tmp/sample.wav")) { ByteArray(10) }

        assertNull(result)
    }

    @Test
    fun buildUploadMetadataSourceMapsWavPathToJsonlSibling() {
        val source = buildUploadMetadataSource("/tmp/sample.wav")

        assertEquals("/tmp/sample.wav", source.wavFile.path)
        assertEquals("/tmp/sample.jsonl", source.jsonlFile.path)
    }

    @Test
    fun readUploadMetadataSnapshotReturnsParsedSnapshotWhenJsonlHasContent() {
        val jsonlFile = File(TestFixtures.tempDir("upload-snapshot"), "sample.jsonl").apply {
            writeText("""{"modified":"confirmed","remote_candidates":["r1","r2"]}""")
        }

        val snapshot = readUploadMetadataSnapshot(jsonlFile) { it.readText() }

        assertEquals("confirmed", snapshot.label)
        assertEquals(2, snapshot.remoteCandidates.length())
    }

    @Test
    fun readUploadMetadataSnapshotFallsBackToEmptySnapshotWhenFileIsBlank() {
        val dir = TestFixtures.tempDir("upload-snapshot-fallback")
        val blank = File(dir, "blank.jsonl").apply { writeText("   ") }

        val blankSnapshot = readUploadMetadataSnapshot(blank) { it.readText() }

        assertEquals("", blankSnapshot.label)
        assertEquals(0, blankSnapshot.remoteCandidates.length())
    }

    @Test
    fun buildUploadPackageReturnsNullWhenMetadataOrRawIsMissing() {
        val missingMetadata = buildUploadPackage(
            path = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadataLoader = { _, _ -> null },
            rawLoader = { JSONArray() }
        )
        val missingRaw = buildUploadPackage(
            path = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadataLoader = { _, _ -> JSONObject() },
            rawLoader = { null }
        )

        assertNull(missingMetadata)
        assertNull(missingRaw)
    }

    @Test
    fun buildUploadPackageReturnsPackagedMetadataAndRaw() {
        val packaged = buildUploadPackage(
            path = "/tmp/sample.wav",
            userId = "tester@example.com",
            metadataLoader = { _, _ -> JSONObject().apply { put("label", "confirmed") } },
            rawLoader = { JSONArray().apply { put(1); put(2) } }
        )

        assertNotNull(packaged)
        assertEquals("confirmed", packaged?.metadata?.getString("label"))
        assertEquals(2, packaged?.raw?.length())
    }

    @Test
    fun buildFeedbackExecutionReadsJsonlPathAndChoosesDispatchRoute() {
        var requestedPath = ""

        val execution = buildFeedbackExecution(
            backendUrl = "https://backend.example.com",
            recognitionUrl = "https://recognition.example.com/process_audio",
            filePath = "/tmp/sample.wav",
            metadataReader = { path ->
                requestedPath = path
                JSONObject().apply {
                    put("local_candidates", JSONArray().apply { put("l1") })
                }
            }
        )

        assertEquals("/tmp/sample.jsonl", execution.jsonlPath)
        assertEquals("/tmp/sample.jsonl", requestedPath)
        assertEquals(FeedbackRoute.POST_PROCESS_AUDIO, execution.dispatchPlan.route)
        assertEquals("https://recognition.example.com/process_audio", execution.dispatchPlan.endpoint)
    }
}
