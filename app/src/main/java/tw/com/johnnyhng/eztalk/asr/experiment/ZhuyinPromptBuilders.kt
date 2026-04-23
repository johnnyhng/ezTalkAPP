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
                task = "預測接在目前輸入之後最可能的 6 個繁體中文漢字或詞語。"
            ),
            userPrompt = buildString {
                appendContext(context)
                appendLine("目前輸入：$sanitizedText")
                appendLine("語氣：${context.selectedEmotionPrompt}")
                appendLine("---")
                appendLine("指令：")
                appendLine("1. 預測目前輸入後最可能的 6 個字詞。")
                appendLine("2. 嚴禁包含輸入中已有的漢字前綴。")
                appendLine("3. 輸出格式：僅回傳後綴，以逗號分隔。不要解釋。")
            }.trim()
        )
    }
}


internal class ZhuyinSentencePromptBuilder {
    fun build(context: ZhuyinPromptContext): PromptTemplate {
        val sanitizedText = context.text.trim()
        return PromptTemplate(
            systemInstruction = zhuyinSystemInstruction(
                task = "預測目前意圖後最可能的 3 個句子的補全部分。"
            ),
            userPrompt = buildString {
                appendContext(context)
                appendLine("目前輸入：$sanitizedText")
                appendLine("語氣：${context.selectedEmotionPrompt}")
                appendLine("---")
                appendLine("指令：")
                appendLine("1. 預測輸入後最可能的 3 個補全內容。")
                appendLine("2. 嚴禁重複輸入中已有的內容。")
                appendLine("3. 格式：使用「|」分詞，標點符號（，。！？）需獨立。")
                appendLine("4. 輸出：僅回傳後綴，以逗號分隔。不要解釋。")
            }.trim()
        )
    }
}

internal class ZhuyinRefinePromptBuilder {
    fun build(context: ZhuyinPromptContext): PromptTemplate {
        return PromptTemplate(
            systemInstruction = zhuyinSystemInstruction(
                task = "將用戶輸入的斷碎詞彙或簡短字詞精煉 (Refine) 為一句完整、禮貌且通順的繁體中文對話句子。"
            ),
            userPrompt = buildString {
                appendContext(context)
                appendLine("原始輸入：${context.text}")
                appendLine("期望語氣：${context.selectedEmotionPrompt}")
                appendLine("---")
                appendLine("Examples:")
                appendLine("input: \"水 渴\" -> answers: 我口渴了，可以給我一杯水嗎？")
                appendLine("input: \"醫生 痛\" -> answers: 醫生，我感覺身體有些地方很痛。")
                appendLine("input: \"外面 走\" -> answers: 我想去外面散散步。")
                appendLine("---")
                appendLine("指令：")
                appendLine("1. 請將「原始輸入」轉換為「一句」最符合語境且通順的完整句子。")
                appendLine("2. 必須考慮「期望語氣」。")
                appendLine("3. 輸出格式：直接回傳該精煉後的句子，不要有任何標題或編號。")
            }.trim()
        )
    }
}

private fun zhuyinSystemInstruction(task: String, customInstruction: String? = null): String {
    return """
        你是一款先進的繁體中文注音輸入輔助工具，服務對象為語言表達能力受限或無法打字的人群（如漸凍症、腦癱、中風後遺症等）。
        你的核心目標是：透過預測與補全注音符號（ㄅㄆㄇ等）、漢字或二者混合內容，降低使用者點擊成本，幫助用戶更高效地完成日常溝通。
        $task
        ${customInstruction?.let { "\n附加情境指令：$it" } ?: ""}

        規則：
        - 回覆必須完全使用繁體中文（台灣口語習慣）。
        - 嚴禁重複：候選內容絕對不能包含「目前輸入」末尾已有的漢字。
        - 簡潔：只回傳預測的補全文字（後綴），以逗號分隔。不要有任何解釋。
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
