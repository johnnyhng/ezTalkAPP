package tw.com.johnnyhng.eztalk.asr.prompt

internal data class SpeakerCommandPromptLine(
    val lineIndex: Int,
    val text: String
)

internal class SpeakerCommandResolutionPromptBuilder {
    fun build(
        utteranceVariants: List<String>,
        commandOptions: List<String>,
        lines: List<SpeakerCommandPromptLine>
    ): PromptTemplate {
        val variantsBlock = utteranceVariants
            .ifEmpty { listOf("(none)") }
            .joinToString(separator = "\n") { "- $it" }

        val commandsBlock = commandOptions.joinToString(separator = "\n") { "- $it" }

        val linesBlock = if (lines.isEmpty()) {
            "(no lines available)"
        } else {
            lines.joinToString(separator = "\n") { line ->
                "- lineIndex=${line.lineIndex} text=${line.text}"
            }
        }

        return PromptTemplate(
            systemInstruction = """
                You resolve a spoken speaker-control utterance into exactly one action.
                Allowed actions are:
                - play_document
                - play_line
                - pause
                - stop
                - no_action

                Use only the provided action space and numbered lines.
                If the intent is uncertain or unsupported, return no_action.
                Return JSON only.
            """.trimIndent(),
            userPrompt = """
                ASR transcript variants collected during one countdown window:
                $variantsBlock

                Available control commands:
                $commandsBlock

                Available document lines:
                $linesBlock
            """.trimIndent(),
            expectedResponseSchema = """
                {
                  "action": "play_document | play_line | pause | stop | no_action",
                  "lineIndex": 0,
                  "confidence": 0.0,
                  "reason": "short explanation"
                }
            """.trimIndent()
        )
    }
}
