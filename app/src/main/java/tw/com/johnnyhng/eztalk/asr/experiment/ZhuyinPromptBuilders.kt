package tw.com.johnnyhng.eztalk.asr.experiment

import tw.com.johnnyhng.eztalk.asr.prompt.PromptTemplate

private const val DEFAULT_CANDIDATE_COUNT = 6

internal data class ZhuyinPromptContext(
    val text: String,
    val candidateCount: Int = DEFAULT_CANDIDATE_COUNT,
    val selectedEmotionPrompt: String = traditionalChineseEmotions.first().prompt,
    val scenarioKeywords: List<String> = emptyList(),
    val scenarioInstruction: String? = null,
    val lastInputSpeech: String? = null,
    val lastOutputSpeech: String? = null,
    val conversationHistory: List<String> = emptyList(),
    val stopSequences: List<String> = emptyList(),
    val maxOutputTokens: Int? = null,
    val temperature: Float? = null
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
                appendLine("1. 請預測接在「目前輸入」之後最可能的「剛好 6 個」候選字詞。")
                appendLine("2. 嚴禁包含「目前輸入」中最後一個完整的詞彙。")
                appendLine("3. 輸出格式：僅回傳候選詞，以繁體半形逗號「,」分隔。不要換行，不要解釋。")
                appendLine("4. 若輸入尾端有注音符號，請先將其轉換為對應漢字後再預測接續詞。")
            }.trim()
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
                if (context.scenarioKeywords.isNotEmpty()) {
                    appendLine("相關領域詞彙：${context.scenarioKeywords.joinToString("、")}")
                }
                appendLine("---")
                appendLine("指令：")
                appendLine("1. 根據「目前輸入」的意圖，預測接下來最可能的「剛好 3 個」補全內容。")
                appendLine("2. 嚴禁在輸出中包含「目前輸入」已有的任何漢字前綴。")
                appendLine("3. 輸出格式：僅回傳補全內容，以繁體半形逗號「,」分隔。不要換行，不要解釋。")
                appendLine("4. 每個候選必須能與「目前輸入」自然連接形成完整、通順的台灣口語句子。")
            }.trim()
        )
    }
}

private fun zhuyinSystemInstruction(task: String, customInstruction: String? = null): String {
    return """
        你是一位服務台灣繁體中文使用者的溝通助手 (AAC Assistant)。
        你的核心目標是：透過預測後續詞句，降低使用者選擇與點擊的成本。
        $task
        ${customInstruction?.let { "\n附加情境指令：$it" } ?: ""}

        規則：
        - 回覆必須完全使用繁體中文（台灣習慣）。
        - 嚴禁重複：候選內容絕對不能包含「目前輸入」末尾已有的漢字。
        - 簡潔：只回傳預測的補全文字，以逗號分隔。不要換行，不要解釋。
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
