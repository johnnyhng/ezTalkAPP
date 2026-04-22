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

        assertTrue(prompt.systemInstruction.contains("溝通助手 (AAC Assistant)"))
        assertTrue(prompt.systemInstruction.contains("嚴禁重複"))
        assertTrue(prompt.userPrompt.contains("目前輸入：我想ㄒ"))
        assertTrue(prompt.userPrompt.contains("語氣：請求"))
        assertTrue(prompt.userPrompt.contains("預測接在「目前輸入」之後最可能的 6 個候選字詞"))
        assertTrue(prompt.expectedResponseSchema?.contains("candidates") == true)
    }

    @Test
    fun sentencePromptRequestsCompleteSentences() {
        val prompt = ZhuyinSentencePromptBuilder().build(
            ZhuyinPromptContext(text = "ㄨㄛˇy", candidateCount = 3)
        )

        assertTrue(prompt.systemInstruction.contains("預測接下來最可能的 3 個完整句子的補全部分"))
        assertTrue(prompt.userPrompt.contains("目前輸入：ㄨㄛˇy"))
        assertTrue(prompt.userPrompt.contains("嚴禁在輸出中包含「目前輸入」已有的任何漢字前綴"))
    }
}
