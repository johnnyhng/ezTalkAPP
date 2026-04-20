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
                You receive utterance variants produced by local ASR for a user with dysarthria.
                Restore exactly one best final sentence from the variants and current context.

                Follow these rules:
                - Prefer the longest variant that is semantically complete.
                - If all variants are noisy or fragmented, infer the most plausible sentence by reconciling phonetic similarity.
                - Ignore obvious noise fragments and filler words.
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
