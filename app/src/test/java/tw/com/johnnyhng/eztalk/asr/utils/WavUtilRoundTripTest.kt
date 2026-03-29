package tw.com.johnnyhng.eztalk.asr.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WavUtilRoundTripTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun saveAsWavWritesFileThatCanBeReadBackAsFloatArray() {
        val userId = "wav-roundtrip-user"
        val filename = "roundtrip-audio"
        val samples = floatArrayOf(-1.0f, -0.5f, 0f, 0.5f, 0.999f)

        val savedPath = saveAsWav(
            context = context,
            samples = samples,
            sampleRate = 16000,
            numChannels = 1,
            userId = userId,
            filename = filename
        )

        assertNotNull(savedPath)
        val file = File(savedPath!!)
        assertTrue(file.exists())
        assertTrue(file.length() > 44)

        val restored = readWavFileToFloatArray(savedPath)

        assertNotNull(restored)
        assertEquals(samples.size, restored?.size)
        assertFloatArrayNear(samples, restored!!, tolerance = 0.0002f)
    }

    @Test
    fun saveAsWavCreatesUserDirectoryWhenMissing() {
        val userId = "wav-create-dir-user"
        val dir = File(context.filesDir, "wavs/$userId").apply {
            deleteRecursively()
        }

        val savedPath = saveAsWav(
            context = context,
            samples = floatArrayOf(0.1f, 0.2f),
            sampleRate = 16000,
            numChannels = 1,
            userId = userId,
            filename = "create-dir"
        )

        assertNotNull(savedPath)
        assertTrue(dir.exists())
    }

    @Test
    fun readWavFileToFloatArrayReturnsNullWhenFileIsMissing() {
        val result = readWavFileToFloatArray("/tmp/missing-audio.wav")

        assertNull(result)
    }

    @Test
    fun readWavFileToFloatArrayReturnsNullWhenFileContainsOnlyHeader() {
        val file = File(context.filesDir, "header-only.wav").apply {
            writeBytes(ByteArray(44))
        }

        val result = readWavFileToFloatArray(file.absolutePath)

        assertNull(result)
    }

    @Test
    fun saveJsonlAndReadJsonlRoundTripMetadata() {
        val userId = "json-roundtrip-user"
        val filename = "metadata-roundtrip"

        val savedPath = saveJsonl(
            context = context,
            userId = userId,
            filename = filename,
            originalText = "original",
            modifiedText = "modified",
            checked = true,
            mutable = false,
            removable = true,
            localCandidates = listOf("local-1"),
            remoteCandidates = listOf("remote-1")
        )

        assertNotNull(savedPath)

        val json = readJsonl(savedPath!!)

        assertNotNull(json)
        assertEquals("original", json?.getString("original"))
        assertEquals("modified", json?.getString("modified"))
        assertEquals(true, json?.getBoolean("checked"))
        assertEquals(false, json?.getBoolean("mutable"))
        assertEquals(true, json?.getBoolean("removable"))
        assertEquals(listOf("local-1"), json?.optStringList("local_candidates"))
        assertEquals(listOf("remote-1"), json?.optStringList("remote_candidates"))
    }

    @Test
    fun saveJsonlOverwritesExistingFileInsteadOfAppending() {
        val userId = "json-overwrite-user"
        val filename = "overwrite"

        val firstPath = saveJsonl(
            context = context,
            userId = userId,
            filename = filename,
            originalText = "first",
            modifiedText = "first",
            checked = false
        )
        val secondPath = saveJsonl(
            context = context,
            userId = userId,
            filename = filename,
            originalText = "second",
            modifiedText = "second",
            checked = true,
            localCandidates = emptyList(),
            remoteCandidates = listOf("remote-1")
        )

        assertEquals(firstPath, secondPath)
        val text = File(secondPath!!).readText()
        val json = JSONObject(text)
        assertEquals(1, text.lines().filter { it.isNotBlank() }.size)
        assertEquals("second", json.getString("original"))
        assertEquals("second", json.getString("modified"))
        assertEquals(true, json.getBoolean("checked"))
        assertEquals(listOf("remote-1"), json.optStringList("remote_candidates"))
    }

    @Test
    fun deleteTranscriptFilesDeletesWavAndJsonlTogether() {
        val userId = "delete-both-user"
        val filename = "delete-both"
        val wavPath = saveAsWav(
            context = context,
            samples = floatArrayOf(0.1f, 0.2f),
            sampleRate = 16000,
            numChannels = 1,
            userId = userId,
            filename = filename
        )!!
        val jsonlPath = saveJsonl(
            context = context,
            userId = userId,
            filename = filename,
            originalText = "original",
            modifiedText = "modified",
            checked = false
        )!!

        val deleted = deleteTranscriptFiles(wavPath)

        assertTrue(deleted)
        assertFalse(File(wavPath).exists())
        assertFalse(File(jsonlPath).exists())
    }

    @Test
    fun deleteTranscriptFilesReturnsTrueWhenFilesAlreadyDoNotExist() {
        val wavPath = File(context.filesDir, "wavs/no-user/missing.wav").absolutePath

        val deleted = deleteTranscriptFiles(wavPath)

        assertTrue(deleted)
    }

    private fun assertFloatArrayNear(expected: FloatArray, actual: FloatArray, tolerance: Float) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index ->
            val delta = kotlin.math.abs(expected[index] - actual[index])
            assertTrue("index=$index expected=${expected[index]} actual=${actual[index]} delta=$delta", delta <= tolerance)
        }
    }
}
