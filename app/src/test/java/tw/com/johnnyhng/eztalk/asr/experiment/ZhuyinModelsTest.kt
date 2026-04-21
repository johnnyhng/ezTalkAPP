package tw.com.johnnyhng.eztalk.asr.experiment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhuyinModelsTest {
    @Test
    fun zhuyinKeyGroupsMatchUpstreamSingleRowLayout() {
        assertEquals(11, zhuyinSingleRowKeyGroups.size)
        assertEquals("ㄅ", zhuyinSingleRowKeyGroups.first().label)
        assertEquals(listOf("ㄅㄆㄇㄈ"), zhuyinSingleRowKeyGroups.first().rows)
        assertEquals("聲詞", zhuyinSingleRowKeyGroups.last().label)
        assertEquals(listOf("␣˙ˊˇˋ"), zhuyinSingleRowKeyGroups.last().rows)
    }

    @Test
    fun traditionalChineseDefaultsIncludeInitialPhrasesAndEmotions() {
        assertTrue("謝謝" in traditionalChineseInitialPhrases)
        assertEquals(listOf("普通", "提問", "拜託", "否定"), traditionalChineseEmotions.map { it.label })
        assertEquals(listOf("陳述", "疑問", "請求", "否定"), traditionalChineseEmotions.map { it.prompt })
    }
}
