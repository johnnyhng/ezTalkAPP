package tw.com.johnnyhng.eztalk.asr.experiment

import org.junit.Assert.assertTrue
import org.junit.Test

class ZhuyinPromptBuildersTest {
    @Test
    fun wordPromptRequestsTraditionalChineseJsonCandidates() {
        val prompt = ZhuyinWordPromptBuilder().build(
            ZhuyinPromptContext(
                text = "我想ㄒ",
                candidateCount = 4,
                selectedEmotionPrompt = "請求",
                conversationHistory = listOf("我想喝水")
            )
        )

        assertTrue(prompt.systemInstruction.contains("繁體中文注音輸入輔助工具"))
        assertTrue(prompt.systemInstruction.contains("只輸出符合 schema 的 JSON"))
        assertTrue(prompt.userPrompt.contains("目前輸入：我想ㄒ"))
        assertTrue(prompt.userPrompt.contains("語氣：請求"))
        assertTrue(prompt.userPrompt.contains("最多 4 個不同候選字詞"))
        assertTrue(prompt.expectedResponseSchema?.contains("candidates") == true)
    }

    @Test
    fun sentencePromptRequestsCompleteSentences() {
        val prompt = ZhuyinSentencePromptBuilder().build(
            ZhuyinPromptContext(text = "ㄨㄛˇy", candidateCount = 3)
        )

        assertTrue(prompt.systemInstruction.contains("完整繁體中文對話句子"))
        assertTrue(prompt.userPrompt.contains("目前輸入：ㄨㄛˇy"))
        assertTrue(prompt.userPrompt.contains("最多 3 個不同完整句子"))
    }
}
