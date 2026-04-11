package tw.com.johnnyhng.eztalk.asr.speaker

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerPlaybackPlanTest {

    @Test
    fun playbackPlanUsesSameLineIndexingAsVisibleContentLines() {
        val text = """
            第一段第一句。第一段第二句
            第二段內容
        """.trimIndent()

        val visibleLines = buildSpeakerContentLines(text)
        val plan = buildSpeakerPlaybackPlan(text)

        assertEquals(
            listOf(
                "第一段第一句",
                "第一段第二句",
                "第二段內容"
            ),
            visibleLines
        )
        assertEquals(visibleLines, plan.segments)
        assertEquals(listOf(0, 1, 2), plan.lineIndexes)
    }
}
