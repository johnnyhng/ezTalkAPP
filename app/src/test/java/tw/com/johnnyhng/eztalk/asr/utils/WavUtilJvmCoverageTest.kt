package tw.com.johnnyhng.eztalk.asr.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures
import java.io.File
import java.io.FileOutputStream

class WavUtilJvmCoverageTest {
    @Test
    fun readWavFileToFloatArrayReturnsSamplesForValidManualWavFile() {
        val samples = floatArrayOf(-1.0f, -0.5f, 0f, 0.5f)
        val pcm = floatSamplesToPcm16(samples)
        val wavFile = File(TestFixtures.tempDir("wav-jvm"), "sample.wav").apply {
            outputStream().use { out ->
                out.write(buildWavHeaderBytes(pcm.size, 16000, 1))
                out.write(pcm)
            }
        }

        val restored = readWavFileToFloatArray(wavFile.absolutePath)

        assertNotNull(restored)
        assertEquals(samples.size, restored?.size)
        assertTrue(kotlin.math.abs(samples[0] - restored!![0]) < 0.0002f)
        assertTrue(kotlin.math.abs(samples[1] - restored[1]) < 0.0002f)
        assertTrue(kotlin.math.abs(samples[2] - restored[2]) < 0.0002f)
        assertTrue(kotlin.math.abs(samples[3] - restored[3]) < 0.0002f)
    }

    @Test
    fun writeWavHeaderWritesExpected44ByteHeaderViaReflection() {
        val file = File(TestFixtures.tempDir("wav-header"), "header.wav")
        val method = Class.forName("tw.com.johnnyhng.eztalk.asr.utils.WavUtilKt")
            .getDeclaredMethod(
                "writeWavHeader",
                FileOutputStream::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).apply {
                isAccessible = true
            }

        FileOutputStream(file).use { out ->
            method.invoke(null, out, 8, 16000, 1)
        }

        val header = file.readBytes()
        assertEquals(44, header.size)
        assertArrayEquals(buildWavHeaderBytes(8, 16000, 1), header)
    }
}
