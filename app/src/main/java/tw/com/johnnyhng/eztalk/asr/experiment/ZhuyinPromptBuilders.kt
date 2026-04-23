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
                if (context.scenarioKeywords.isNotEmpty()) {
                    appendLine("優先語境詞彙：${context.scenarioKeywords.joinToString("、")}")
                }
                appendLine("---")
                appendLine("Examples:")
                appendLine("sentence: \"ㄋ\" -> answers: 你,您,呢,那")
                appendLine("sentence: \"我想ㄒ\" -> answers: 想,寫,洗,休息,學")
                appendLine("sentence: \"今天天氣ㄗ\" -> answers: 怎麼,真好,讚")
                appendLine("---")
                appendLine("指令：")
                appendLine("1. 預測接在「目前輸入」之後最可能的 6 個候選字詞。")
                appendLine("2. 嚴禁包含「目前輸入」中最後一個完整的詞彙。")
                appendLine("3. 若注音結尾不完整（如缺少介音或韻母），請推測合理的完整注音。")
                appendLine("4. 輸出格式：僅回傳候選詞，以逗號「,」分隔。不要換行，不要解釋。")
            }.trim()
        )
    }
}

internal class ZhuyinSentencePromptBuilder {
    fun build(context: ZhuyinPromptContext): PromptTemplate {
        val sanitizedText = context.text.trim()
        return PromptTemplate(
            systemInstruction = zhuyinSystemInstruction(
                task = "將目前輸入的注音、漢字或混合內容補全為接續在後的完整對話句子。"
            ),
            userPrompt = buildString {
                appendContext(context)
                appendLine("目前輸入：$sanitizedText")
                appendLine("語氣：${context.selectedEmotionPrompt}")
                if (context.scenarioKeywords.isNotEmpty()) {
                    appendLine("相關領域詞彙：${context.scenarioKeywords.joinToString("、")}")
                }
                appendLine("---")
                appendLine("Examples:")
                appendLine("sentence: \"我想ㄔ\" -> answers: 吃|點|東西|。,吃|藥|。,出|門|。")
                appendLine("sentence: \"好ㄇ\" -> answers: 好|嗎|？, 好|啊|！")
                appendLine("sentence: \"ㄊㄥˊㄊㄥˊ\" -> answers: 疼|疼|的|地方|在|這裡|。,這|裡|疼|，|請|輕|一點|。")
                appendLine("---")
                appendLine("指令：")
                appendLine("1. 根據「目前輸入」的意圖，預測接下來最可能的 3 個補全內容（Suffix）。")
                appendLine("2. 嚴禁包含「目前輸入」已有的任何漢字前綴。")
                appendLine("3. 輸出格式：使用「|」符號進行語義分詞（例如：想|去|散步|嗎|？）。")
                appendLine("4. 標點符號必須獨立為一個片段（例如：。|？|，|！）。")
                appendLine("5. 以逗號「,」分隔不同的候選語句。不要換行，不要解釋。")
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
