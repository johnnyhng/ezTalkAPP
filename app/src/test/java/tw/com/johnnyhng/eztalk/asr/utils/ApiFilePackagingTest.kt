package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiFilePackagingTest {
    @Test
    fun readWavFileToJsonArrayReturnsNullWhenFileIsMissing() {
        val result = readWavFileToJsonArray("/tmp/does-not-exist.wav")

        assertNull(result)
    }

    @Test
    fun readWavFileToJsonArrayReturnsNullWhenFileIsSmallerThanWavHeader() {
        val wavFile = createTempWavFile(
            "tiny.wav",
            ByteArray(10) { it.toByte() }
        )

        val result = readWavFileToJsonArray(wavFile.absolutePath)

        assertNull(result)
    }

    @Test
    fun readWavFileToJsonArrayReturnsUnsignedByteValuesForValidWavSizedFile() {
        val bytes = ByteArray(46).also {
            it[0] = 0
            it[1] = 1
            it[44] = (-1).toByte()
            it[45] = 127
        }
        val wavFile = createTempWavFile("valid.wav", bytes)

        val result = readWavFileToJsonArray(wavFile.absolutePath)

        assertNotNull(result)
        assertEquals(46, result?.length())
        assertEquals(0, result?.getInt(0))
        assertEquals(1, result?.getInt(1))
        assertEquals(255, result?.getInt(44))
        assertEquals(127, result?.getInt(45))
    }

    @Test
    fun packageUploadJsonMetadataReadsModifiedTextAndRemoteCandidatesFromJsonl() {
        val dir = TestFixtures.tempDir("api-metadata")
        val wavFile = File(dir, "sample.wav").apply {
            writeBytes(ByteArray(44) { 0 })
        }
        File(dir, "sample.jsonl").writeText(
            """
            {
              "modified": "confirmed text",
              "remote_candidates": ["r1", "r2"]
            }
            """.trimIndent()
        )

        val result = packageUploadJsonMetadata(wavFile.absolutePath, "tester@example.com")

        assertNotNull(result)
        assertEquals("tester", result?.getJSONObject("account")?.getString("user_id"))
        assertEquals("confirmed text", result?.getString("label"))
        assertEquals("confirmed text", result?.getString("sentence"))
        assertEquals("sample.wav", result?.getString("filename"))
        assertEquals(listOf("r1", "r2"), result?.getJSONArray("remote_candidates")?.toStringList())
    }

    @Test
    fun packageUploadJsonMetadataFallsBackToEmptyFieldsWhenJsonlIsMalformed() {
        val dir = TestFixtures.tempDir("api-bad-jsonl")
        val wavFile = File(dir, "sample.wav").apply {
            writeBytes(ByteArray(44) { 0 })
        }
        File(dir, "sample.jsonl").writeText("{broken-json")

        val result = packageUploadJsonMetadata(wavFile.absolutePath, "tester@example.com")

        assertNotNull(result)
        assertEquals("", result?.getString("label"))
        assertTrue(result?.getJSONArray("remote_candidates")?.length() == 0)
    }

    @Test
    fun packageUploadJsonReturnsNullWhenWavCannotBeReadAsValidAudioPayload() {
        val dir = TestFixtures.tempDir("api-invalid-wav")
        val wavFile = File(dir, "broken.wav").apply {
            writeBytes(ByteArray(8) { 0 })
        }
        File(dir, "broken.jsonl").writeText("""{"modified":"text"}""")

        val result = packageUploadJson(wavFile.absolutePath, "tester@example.com")

        assertNull(result)
    }

    @Test
    fun packageUploadJsonIncludesMetadataAndRawArrayWhenFilesAreValid() {
        val dir = TestFixtures.tempDir("api-upload-json")
        val wavBytes = ByteArray(45).also {
            it[44] = 9
        }
        val wavFile = File(dir, "sample.wav").apply {
            writeBytes(wavBytes)
        }
        File(dir, "sample.jsonl").writeText(
            """
            {
              "modified": "confirmed text",
              "remote_candidates": ["r1"]
            }
            """.trimIndent()
        )

        val result = packageUploadJson(wavFile.absolutePath, "tester@example.com")

        assertNotNull(result)
        assertEquals("confirmed text", result?.getString("label"))
        assertEquals("sample.wav", result?.getString("filename"))
        assertEquals(45, result?.getJSONArray("raw")?.length())
        assertEquals(9, result?.getJSONArray("raw")?.getInt(44))
    }

    private fun createTempWavFile(name: String, bytes: ByteArray): File =
        File(TestFixtures.tempDir("api-wav"), name).apply {
            writeBytes(bytes)
        }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { index -> getString(index) }
}
