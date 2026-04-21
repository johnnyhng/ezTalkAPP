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
                task = "將注音、漢字或混合輸入補全為接續在目前文字後方的繁體中文候選字詞。"
            ),
            userPrompt = buildString {
                appendContext(context)
                appendLine("目前輸入：$sanitizedText")
                appendLine("語氣：${context.selectedEmotionPrompt}")
                appendLine("請生成最多 ${context.candidateCount} 個不同候選字詞。")
                appendLine("候選字詞應能接在目前輸入之後，並優先符合日常、醫療、休息、照護溝通情境。")
                appendLine("若目前輸入尾端包含不完整注音，請推測合理完整注音後轉成繁體中文。")
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
                task = "將注音、漢字或混合輸入補全為完整繁體中文對話句子。"
            ),
            userPrompt = buildString {
                appendContext(context)
                appendLine("目前輸入：$sanitizedText")
                appendLine("語氣：${context.selectedEmotionPrompt}")
                appendLine("請生成最多 ${context.candidateCount} 個不同完整句子。")
                appendLine("每個句子都應以目前輸入的意圖開頭或延伸，不要偏離使用者可能要表達的內容。")
                appendLine("若目前輸入尾端包含不完整注音，請推測合理完整注音後產生繁體中文句子。")
            }.trim(),
            expectedResponseSchema = zhuyinCandidatesSchema()
        )
    }
}

private fun zhuyinSystemInstruction(task: String): String {
    return """
        你是一款繁體中文注音輸入輔助工具，服務對象為語言表達能力受限或無法打字的人。
        $task

        規則：
        - 回覆必須完全使用繁體中文。
        - 可以理解注音符號、聲調、漢字、以及少量混合羅馬字。
        - 不要輸出解釋、Markdown、編號或額外文字。
        - 只輸出符合 schema 的 JSON。
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
