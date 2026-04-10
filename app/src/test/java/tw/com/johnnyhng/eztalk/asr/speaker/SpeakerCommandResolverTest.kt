package tw.com.johnnyhng.eztalk.asr.speaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakerCommandResolverTest {

    private val lines = listOf(
        "第一行",
        "第二行",
        "第三行"
    )

    @Test
    fun resolvesPlayKeywordsWithCommonAsrVariants() {
        assertEquals(SpeakerContentCommand.Play, resolveSpeakerContentCommand("播放", lines))
        assertEquals(SpeakerContentCommand.Play, resolveSpeakerContentCommand("撥放", lines))
        assertEquals(SpeakerContentCommand.Play, resolveSpeakerContentCommand("繼續", lines))
    }

    @Test
    fun resolvesPauseAndStopKeywordsWithCommonAsrVariants() {
        assertEquals(SpeakerContentCommand.Pause, resolveSpeakerContentCommand("暫停", lines))
        assertEquals(SpeakerContentCommand.Pause, resolveSpeakerContentCommand("暂停播放", lines))
        assertEquals(SpeakerContentCommand.Stop, resolveSpeakerContentCommand("停播", lines))
        assertEquals(SpeakerContentCommand.Stop, resolveSpeakerContentCommand("停止播放", lines))
    }

    @Test
    fun resolvesPlayLineWhenLineSuffixIsMisrecognized() {
        assertEquals(SpeakerContentCommand.PlayLine(2), resolveSpeakerContentCommand("第三航", lines))
        assertEquals(SpeakerContentCommand.PlayLine(1), resolveSpeakerContentCommand("第2號", lines))
        assertEquals(SpeakerContentCommand.PlayLine(0), resolveSpeakerContentCommand("第一項", lines))
    }

    @Test
    fun returnsNullWhenRequestedLineIsOutOfRange() {
        assertNull(resolveSpeakerContentCommand("第五行", lines))
    }
}
