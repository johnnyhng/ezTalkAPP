package tw.com.johnnyhng.eztalk.asr.speaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakerCommandResolverTest {
    private val lines = listOf("第一行", "第二行", "第三行")

    @Test
    fun resolvesPlayCommand() {
        assertEquals(
            SpeakerContentCommand.Play,
            resolveSpeakerContentCommand("播放", lines)
        )
    }

    @Test
    fun resolvesPauseCommand() {
        assertEquals(
            SpeakerContentCommand.Pause,
            resolveSpeakerContentCommand("暫停", lines)
        )
    }

    @Test
    fun resolvesStopCommand() {
        assertEquals(
            SpeakerContentCommand.Stop,
            resolveSpeakerContentCommand("停止", lines)
        )
    }

    @Test
    fun resolvesNumericLineCommand() {
        assertEquals(
            SpeakerContentCommand.PlayLine(1),
            resolveSpeakerContentCommand("播放第2行", lines)
        )
    }

    @Test
    fun resolvesChineseLineCommand() {
        assertEquals(
            SpeakerContentCommand.PlayLine(2),
            resolveSpeakerContentCommand("播放第三行", lines)
        )
    }

    @Test
    fun ignoresOutOfRangeLineCommand() {
        assertNull(resolveSpeakerContentCommand("播放第9行", lines))
    }
}
