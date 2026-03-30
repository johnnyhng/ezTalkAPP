package tw.com.johnnyhng.eztalk.asr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavUtilReadWavFileTest {
    @Test
    fun readWavFileToFloatArrayReturnsDecodedSamplesForValidWavFile() {
        val wavFile = File(TestFixtures.tempDir("wav-read-wrapper"), "sample.wav")
        val header = buildWavHeaderBytes(
            pcmDataSize = 4,
            sampleRate = 16000,
            numChannels = 1
        )
        val pcm = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort((-16384).toShort())
            .putShort(8192.toShort())
            .array()
        wavFile.writeBytes(header + pcm)

        val result = readWavFileToFloatArray(wavFile.absolutePath)

        assertNotNull(result)
        assertEquals(2, result?.size)
        assertEquals(-0.5f, result!![0], 0.0001f)
        assertEquals(0.25f, result[1], 0.0001f)
    }
}
