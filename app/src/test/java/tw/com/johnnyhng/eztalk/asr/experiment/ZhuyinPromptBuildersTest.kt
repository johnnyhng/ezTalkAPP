package tw.com.johnnyhng.eztalk.asr.experiment

import org.junit.Assert.assertTrue
import org.junit.Test

class ZhuyinPromptBuildersTest {
    @Test
    fun wordPromptRequestsTraditionalChineseJsonCandidates() {
        val prompt = ZhuyinWordPromptBuilder().build(
            ZhuyinPromptContext(
                text = "我想ㄒ",
                candidateCount = 6,
                selectedEmotionPrompt = "請求",
                conversationHistory = listOf("我想喝水")
            )
        )

        assertTrue(prompt.systemInstruction.contains("繁體中文注音輸入輔助工具"))
        assertTrue(prompt.systemInstruction.contains("嚴禁重複"))
        assertTrue(prompt.userPrompt.contains("目前輸入：我想ㄒ"))
        assertTrue(prompt.userPrompt.contains("語氣：請求"))
        assertTrue(prompt.userPrompt.contains("預測目前輸入後最可能的 6 個字詞"))
    }

    @Test
    fun sentencePromptRequestsCompleteSentences() {
        val prompt = ZhuyinSentencePromptBuilder().build(
            ZhuyinPromptContext(text = "ㄨㄛˇy", candidateCount = 3)
        )

        assertTrue(prompt.systemInstruction.contains("預測目前意圖後最可能的 3 個句子的補全部分"))
        assertTrue(prompt.userPrompt.contains("目前輸入：ㄨㄛˇy"))
        assertTrue(prompt.userPrompt.contains("嚴禁重複輸入中已有的內容"))
    }
}
