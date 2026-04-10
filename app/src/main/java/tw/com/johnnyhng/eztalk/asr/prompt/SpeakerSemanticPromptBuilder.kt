package tw.com.johnnyhng.eztalk.asr.prompt

internal data class SpeakerSemanticPromptCandidate(
    val lineStart: Int,
    val lineEnd: Int,
    val text: String
)

internal class SpeakerSemanticPromptBuilder {
    fun build(
        asrText: String,
        candidates: List<SpeakerSemanticPromptCandidate>
    ): PromptTemplate {
        val candidateBlock = if (candidates.isEmpty()) {
            "No candidates available."
        } else {
            candidates.joinToString(separator = "\n\n") { candidate ->
                "Candidate ${candidate.lineStart}-${candidate.lineEnd}:\n${candidate.text}"
            }
        }

        return PromptTemplate(
            systemInstruction = """
                You resolve a spoken command to one of the provided document candidates.
                Only choose from the provided candidates.
                If none match, return no_match.
            """.trimIndent(),
            userPrompt = """
                ASR text:
                $asrText

                Candidate chunks:
                $candidateBlock
            """.trimIndent(),
            expectedResponseSchema = """
                {
                  "decision": "match | no_match | ambiguous",
                  "lineStart": 0,
                  "lineEnd": 0,
                  "reason": "short explanation"
                }
            """.trimIndent()
        )
    }
}
