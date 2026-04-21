package tw.com.johnnyhng.eztalk.asr.experiment

import org.junit.Assert.assertEquals
import org.junit.Test

class ZhuyinTextEngineTest {
    @Test
    fun stripTrailingZhuyinRemovesBopomofoRun() {
        assertEquals("我想", stripTrailingZhuyin("我想ㄒㄧㄤˇ"))
    }

    @Test
    fun stripTrailingZhuyinKeepsNonTrailingBopomofo() {
        assertEquals("ㄋㄧˇ好", stripTrailingZhuyin("ㄋㄧˇ好"))
    }

    @Test
    fun appendZhuyinCandidateReplacesPureTrailingZhuyin() {
        assertEquals("你", appendZhuyinCandidate("ㄋㄧˇ", "你"))
    }

    @Test
    fun appendZhuyinCandidateReplacesMixedChineseTrailingZhuyin() {
        assertEquals("我想休息", appendZhuyinCandidate("我想ㄒ", "休息"))
    }

    @Test
    fun appendZhuyinCandidateHandlesToneMarks() {
        assertEquals("不要", appendZhuyinCandidate("ㄅㄨˋ", "不要"))
    }

    @Test
    fun appendZhuyinCandidateDropsLeadingDashFromCandidate() {
        assertEquals("我想吃", appendZhuyinCandidate("我想ㄔ", "-吃"))
    }

    @Test
    fun appendZhuyinCandidateAppendsWhenNoTrailingZhuyinExists() {
        assertEquals("今天天氣很好", appendZhuyinCandidate("今天天氣", "很好"))
    }

    @Test
    fun segmentAndJoinTraditionalChineseUseCharacterUnits() {
        val segments = segmentTraditionalChinese("謝謝你")

        assertEquals(listOf("謝", "謝", "你"), segments)
        assertEquals("謝謝你", joinTraditionalChinese(segments))
    }
}
