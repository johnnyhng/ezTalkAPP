package tw.com.johnnyhng.eztalk.asr.prompt

internal class TranscriptCorrectionPromptBuilder {
    fun build(
        utteranceVariants: List<String>,
        contextLines: List<String>
    ): PromptTemplate {
        val variantsBlock = utteranceVariants
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("(none)") }
            .joinToString(separator = "\n") { "- $it" }

        val contextBlock = contextLines
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("(no context available)") }
            .joinToString(separator = "\n") { "- $it" }

        return PromptTemplate(
            systemInstruction = """
                You are an ASR correction and semantic restoration engine for ezTalk.
                You receive utterance variants produced by local ASR and backend recognition for a user with dysarthria.
                Restore exactly one best final sentence from the variants and current context.

                Follow these rules:
                - Treat backend recognition only as additional variants; do not trust it automatically.
                - Your task is not limited to selecting an existing variant.
                - If one variant is already semantically complete, choose or lightly correct the best one.
                - If all variants are incomplete, fragmented, or phonetically corrupted, restore a complete sentence by combining evidence from variants, phonetic similarity, repeated fragments, and current context.
                - The restored sentence may contain words that are not exactly present in any single variant, but every added word must be strongly supported by the variants or context.
                - Prefer longer variants when they are coherent and contextually plausible.
                - If multiple variants have similar length, prefer the wording supported by repeated characters, repeated words, or phonetic similarity across variants.
                - Do not hallucinate unrelated facts, names, or intent that are not supported by variants or context.
                - Ignore obvious noise fragments and filler words.
                - If the corrected text contains Simplified Chinese, convert it to Traditional Chinese before returning it.
                - Only return a non-empty corrected_text when the selected or restored sentence is reliable enough to auto-replace the transcript.
                - If confidence is below 0.85, return an empty corrected_text.
                - Always return strict JSON only.
                - confidence must be a number between 0.0 and 1.0.
                - reasoning must be short and non-empty.
            """.trimIndent(),
            userPrompt = """
                Variants:
                $variantsBlock

                Current Context:
                $contextBlock
            """.trimIndent(),
            expectedResponseSchema = """
                {
                  "corrected_text": "restored final ASR result",
                  "confidence": 0.91,
                  "reasoning": "selected the longest coherent variant and corrected a phonetic error"
                }
            """.trimIndent()
        )
    }
}
