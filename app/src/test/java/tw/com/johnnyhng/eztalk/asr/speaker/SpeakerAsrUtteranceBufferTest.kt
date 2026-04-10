package tw.com.johnnyhng.eztalk.asr.speaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakerAsrUtteranceBufferTest {

    @Test
    fun buildReturnsNullWhenNoTranscriptWasCollected() {
        val buffer = SpeakerAsrUtteranceBuffer()

        assertNull(buffer.build(finalTextVersion = 1))
    }

    @Test
    fun collectsUniqueTranscriptVariantsInStableOrder() {
        val buffer = SpeakerAsrUtteranceBuffer()

        buffer.add("播放")
        buffer.add(" 播放 ")
        buffer.add("播放。")
        buffer.add("撥放")
        buffer.add("播放第三行")

        val bundle = buffer.build(finalTextVersion = 3)

        requireNotNull(bundle)
        assertEquals("播放第三行", bundle.primaryText)
        assertEquals(
            listOf("播放", "撥放", "播放第三行"),
            bundle.variants
        )
        assertEquals(3, bundle.finalTextVersion)
    }

    @Test
    fun resetClearsCollectedVariants() {
        val buffer = SpeakerAsrUtteranceBuffer()

        buffer.add("播放")
        buffer.reset()

        assertEquals(emptyList<String>(), buffer.variants())
        assertNull(buffer.build(finalTextVersion = 2))
    }
}
