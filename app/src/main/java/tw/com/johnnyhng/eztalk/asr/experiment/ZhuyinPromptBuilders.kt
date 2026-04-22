package tw.com.johnnyhng.eztalk.asr.experiment

import tw.com.johnnyhng.eztalk.asr.prompt.PromptTemplate

private const val DEFAULT_CANDIDATE_COUNT = 6

internal data class ZhuyinPromptContext(
    val text: String,
    val candidateCount: Int = DEFAULT_CANDIDATE_COUNT,
    val selectedEmotionPrompt: String = traditionalChineseEmotions.first().prompt,
    val lastInputSpeech: String? = null,
    val lastOutputSpeech: String? = null,
    val conversationHistory: List<String> = emptyList()
)

internal class ZhuyinWordPromptBuilder {
    fun build(context: ZhuyinPromptContext): PromptTemplate {
        val sanitizedText = context.text.trim()
        return PromptTemplate(
            systemInstruction = zhuyinSystemInstruction(
                task = "預測目前繁體中文輸入後方最可能的 6 個後續字詞。"
            ),
            userPrompt = buildString {
                appendContext(context)
                appendLine("目前輸入：$sanitizedText")
                appendLine("語氣：${context.selectedEmotionPrompt}")
                appendLine("---")
                appendLine("指令：")
                appendLine("1. 請預測接在「目前輸入」之後最可能的 6 個候選字詞。")
                appendLine("2. 嚴禁包含「目前輸入」中最後一個完整的詞彙。")
                appendLine("3. 輸出必須僅包含預測的補全部分，不要重複前綴。")
                appendLine("4. 若輸入尾端有注音符號，請先將其轉換為對應漢字後再預測接續詞。")
            }.trim(),
            expectedResponseSchema = zhuyinCandidatesSchema()
        )
    }
}

internal class ZhuyinSentencePromptBuilder {
    fun build(context: ZhuyinPromptContext): PromptTemplate {
        val sanitizedText = context.text.trim()
        return PromptTemplate(
            systemInstruction = zhuyinSystemInstruction(
                task = "根據目前繁體中文輸入的意圖，預測接下來最可能的 3 個完整句子的補全部分。"
            ),
            userPrompt = buildString {
                appendContext(context)
                appendLine("目前輸入：$sanitizedText")
                appendLine("語氣：${context.selectedEmotionPrompt}")
                appendLine("---")
                appendLine("指令：")
                appendLine("1. 根據「目前輸入」的意圖，預測接下來最可能的 3 個補全內容。")
                appendLine("2. 嚴禁在輸出中包含「目前輸入」已有的任何漢字前綴。")
                appendLine("3. 每個候選必須能與「目前輸入」自然連接形成完整、通順的台灣口語句子。")
                appendLine("4. 優先使用台灣常用語助詞（啦、喔、呢、吧）。")
            }.trim(),
            expectedResponseSchema = zhuyinCandidatesSchema()
        )
    }
}

private fun zhuyinSystemInstruction(task: String): String {
    return """
        你是一位服務台灣繁體中文使用者的溝通助手 (AAC Assistant)，服務對象為表達能力受限的人。
        你的核心目標是：透過預測後續詞句，降低使用者選擇與點擊的成本。
        $task

        規則：
        - 回覆必須完全使用繁體中文（台灣習慣）。
        - 輸出格式必須為 JSON array，包含在 "candidates" 欄位中。
        - 嚴禁重複：候選內容絕對不能包含「目前輸入」末尾已有的漢字。
        - 簡潔：只回傳預測的補全文字。
    """.trimIndent()
}

private fun StringBuilder.appendContext(context: ZhuyinPromptContext) {
    context.lastOutputSpeech?.takeIf { it.isNotBlank() }?.let {
        appendLine("你：$it")
    }
    context.lastInputSpeech?.takeIf { it.isNotBlank() }?.let {
        appendLine("使用者：$it")
    }
    val history = context.conversationHistory
        .filter { it.isNotBlank() }
        .takeLast(8)
    if (history.isNotEmpty()) {
        appendLine("對話歷史：")
        history.forEach { appendLine("- $it") }
    }
}

private fun zhuyinCandidatesSchema(): String {
    return """
        {
          "candidates": [
            "繁體中文候選"
          ]
        }
    """.trimIndent()
}
