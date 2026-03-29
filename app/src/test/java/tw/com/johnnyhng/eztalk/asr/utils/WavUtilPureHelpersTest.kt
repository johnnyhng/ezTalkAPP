package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavUtilPureHelpersTest {
    @Test
    fun floatSamplesToPcm16ClampsAndEncodesLittleEndianSamples() {
        val pcm = floatSamplesToPcm16(
            floatArrayOf(-1.5f, -1.0f, 0f, 0.5f, 1.5f)
        )
        val shorts = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        assertEquals(5, shorts.remaining())
        assertEquals(Short.MIN_VALUE.toInt(), shorts.get(0).toInt())
        assertEquals(-32767, shorts.get(1).toInt())
        assertEquals(0, shorts.get(2).toInt())
        assertEquals(16383, shorts.get(3).toInt())
        assertEquals(Short.MAX_VALUE.toInt(), shorts.get(4).toInt())
    }

    @Test
    fun buildTranscriptJsonLineIncludesExpectedFieldsAndTrailingNewline() {
        val line = buildTranscriptJsonLine(
            originalText = "original",
            modifiedText = "modified",
            checked = true,
            mutable = false,
            removable = true,
            localCandidates = listOf("local-1"),
            remoteCandidates = listOf("remote-1")
        )

        assertTrue(line.endsWith("\n"))
        val json = JSONObject(line.trim())
        assertEquals("original", json.getString("original"))
        assertEquals("modified", json.getString("modified"))
        assertEquals(true, json.getBoolean("checked"))
        assertEquals(false, json.getBoolean("mutable"))
        assertEquals(true, json.getBoolean("removable"))
        assertEquals(listOf("local-1"), json.optStringList("local_candidates"))
        assertEquals(listOf("remote-1"), json.optStringList("remote_candidates"))
    }

    @Test
    fun buildTranscriptJsonLineOmitsCandidateArraysWhenNull() {
        val line = buildTranscriptJsonLine(
            originalText = "original",
            modifiedText = "modified",
            checked = false
        )

        val json = JSONObject(line.trim())
        assertFalse(json.has("local_candidates"))
        assertFalse(json.has("remote_candidates"))
    }

    @Test
    fun buildWavHeaderBytesEncodesRiffMetadata() {
        val header = buildWavHeaderBytes(
            pcmDataSize = 8,
            sampleRate = 16000,
            numChannels = 1
        )

        assertEquals(44, header.size)
        assertEquals("RIFF", String(header.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(header.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals("fmt ", String(header.copyOfRange(12, 16), Charsets.US_ASCII))
        assertEquals("data", String(header.copyOfRange(36, 40), Charsets.US_ASCII))
        assertEquals(1, header[22].toInt())
    }

    @Test
    fun parseWavAudioBytesReturnsNullWhenOnlyHeaderExists() {
        val result = parseWavAudioBytes(ByteArray(44))

        assertNull(result)
    }

    @Test
    fun parseWavAudioBytesDecodes16BitPcmSamplesAfterHeader() {
        val header = buildWavHeaderBytes(
            pcmDataSize = 4,
            sampleRate = 16000,
            numChannels = 1
        )
        val pcm = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort((-32768).toShort())
            .putShort(16384.toShort())
            .array()

        val result = parseWavAudioBytes(header + pcm)

        assertNotNull(result)
        assertEquals(2, result?.size)
        assertEquals(-1.0f, result!![0], 0.0001f)
        assertEquals(0.5f, result[1], 0.0001f)
    }
}
