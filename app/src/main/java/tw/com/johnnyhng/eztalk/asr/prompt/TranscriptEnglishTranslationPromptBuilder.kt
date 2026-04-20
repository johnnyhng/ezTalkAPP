package tw.com.johnnyhng.eztalk.asr.prompt

internal class TranscriptEnglishTranslationPromptBuilder {
    fun build(
        sourceText: String
    ): PromptTemplate {
        return PromptTemplate(
            systemInstruction = """
                You translate ezTalk transcript text from Chinese into natural English.
                Preserve the original intent and tone.
                Return English only.
                Do not explain.
                Do not add extra context.
                Always return strict JSON only.
            """.trimIndent(),
            userPrompt = """
                Source text:
                $sourceText
            """.trimIndent(),
            expectedResponseSchema = """
                {
                  "translated_text": "natural English translation"
                }
            """.trimIndent()
        )
    }
}
