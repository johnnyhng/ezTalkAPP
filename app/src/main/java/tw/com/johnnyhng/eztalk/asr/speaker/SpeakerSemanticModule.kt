package tw.com.johnnyhng.eztalk.asr.speaker

import tw.com.johnnyhng.eztalk.asr.llm.LlmProvider
import tw.com.johnnyhng.eztalk.asr.llm.LlmRequest
import tw.com.johnnyhng.eztalk.asr.llm.LlmResponse
import tw.com.johnnyhng.eztalk.asr.prompt.SpeakerSemanticPromptBuilder
import tw.com.johnnyhng.eztalk.asr.prompt.SpeakerSemanticPromptCandidate

internal class SpeakerSemanticModule(
    private val semanticSearch: SpeakerSemanticSearch = SpeakerSemanticSearch(),
    private val config: SpeakerSemanticSearchConfig = SpeakerSemanticSearchConfig(),
    private val llmProvider: LlmProvider? = null,
    private val promptBuilder: SpeakerSemanticPromptBuilder = SpeakerSemanticPromptBuilder(),
    private val llmModel: String = "gemini-2.5-flash"
) {
    fun canUseLlmFallback(): Boolean {
        return llmProvider != null
    }

    fun resolve(
        queryText: String,
        lines: List<String>,
        chunks: List<SpeakerIndexedChunk>
    ): SpeakerSemanticResolution {
        val query = semanticSearch.buildQuery(queryText)
        val rankedResults = semanticSearch.rank(query = query, chunks = chunks)
        val bestResult = rankedResults.firstOrNull { it.finalScore >= config.minimumScoreThreshold }
            ?: return SpeakerSemanticResolution(
                query = query,
                rankedResults = rankedResults,
                decision = SpeakerSemanticDecision.NoMatch
            )

        val matchedLineIndex = resolveMatchedLineIndex(lines = lines, result = bestResult)
        val decision = if (bestResult.finalScore >= config.autoPlayScoreThreshold) {
            SpeakerSemanticDecision.AutoPlay(
                lineIndex = matchedLineIndex,
                result = bestResult
            )
        } else {
            SpeakerSemanticDecision.Candidate(
                lineIndex = matchedLineIndex,
                result = bestResult
            )
        }

        return SpeakerSemanticResolution(
            query = query,
            rankedResults = rankedResults,
            decision = decision
        )
    }

    fun buildLlmRequest(
        queryText: String,
        rankedResults: List<SpeakerSearchResult>,
        maxCandidates: Int = 5
    ): LlmRequest? {
        if (queryText.isBlank() || rankedResults.isEmpty()) return null

        val prompt = promptBuilder.build(
            asrText = queryText,
            candidates = rankedResults
                .take(maxCandidates.coerceAtLeast(1))
                .map { result ->
                    SpeakerSemanticPromptCandidate(
                        lineStart = result.lineStart,
                        lineEnd = result.lineEnd,
                        text = result.matchedText
                    )
                }
        )

        return LlmRequest(
            model = llmModel,
            systemInstruction = prompt.systemInstruction,
            userPrompt = prompt.userPrompt,
            outputFormat = tw.com.johnnyhng.eztalk.asr.llm.LlmOutputFormat.JSON,
            schemaHint = prompt.expectedResponseSchema
        )
    }

    fun parseLlmResponse(
        response: LlmResponse,
        rankedResults: List<SpeakerSearchResult>,
        lines: List<String>
    ): SpeakerSemanticDecision {
        val payload = parseSemanticPayload(response.rawText) ?: return SpeakerSemanticDecision.NoMatch

        return when (payload.decision.lowercase()) {
            "match", "candidate" -> {
                val matched = findMatchingResult(payload, rankedResults)
                if (matched == null) {
                    SpeakerSemanticDecision.NoMatch
                } else {
                    SpeakerSemanticDecision.Candidate(
                        lineIndex = resolveMatchedLineIndex(lines, matched),
                        result = matched
                    )
                }
            }

            "autoplay", "play" -> {
                val matched = findMatchingResult(payload, rankedResults)
                if (matched == null) {
                    SpeakerSemanticDecision.NoMatch
                } else {
                    SpeakerSemanticDecision.AutoPlay(
                        lineIndex = resolveMatchedLineIndex(lines, matched),
                        result = matched
                    )
                }
            }

            "ambiguous" -> {
                SpeakerSemanticDecision.Ambiguous(
                    candidates = rankedResults.take(3)
                )
            }

            else -> SpeakerSemanticDecision.NoMatch
        }
    }

    private fun findMatchingResult(
        payload: SpeakerLlmSemanticPayload,
        rankedResults: List<SpeakerSearchResult>
    ): SpeakerSearchResult? {
        val start = payload.lineStart
        val end = payload.lineEnd
        if (start == null || end == null) return rankedResults.firstOrNull()

        return rankedResults.firstOrNull { result ->
            result.lineStart == start && result.lineEnd == end
        } ?: rankedResults.firstOrNull { result ->
            start in result.lineStart..result.lineEnd || end in result.lineStart..result.lineEnd
        }
    }

    private fun parseSemanticPayload(rawText: String): SpeakerLlmSemanticPayload? {
        val normalized = rawText.trim()
        if (normalized.isBlank()) return null

        fun extractString(key: String): String? {
            val pattern = """"$key"\s*:\s*"([^"]*)"""".toRegex()
            return pattern.find(normalized)?.groupValues?.getOrNull(1)
        }

        fun extractInt(key: String): Int? {
            val pattern = """"$key"\s*:\s*(-?\d+)""".toRegex()
            return pattern.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        val decision = extractString("decision")
            ?: normalized.lowercase().takeIf {
                it == "match" || it == "candidate" || it == "autoplay" || it == "play" || it == "ambiguous" || it == "no_match"
            }
            ?: return null

        return SpeakerLlmSemanticPayload(
            decision = decision,
            lineStart = extractInt("lineStart"),
            lineEnd = extractInt("lineEnd"),
            reason = extractString("reason")
        )
    }

    private fun resolveMatchedLineIndex(
        lines: List<String>,
        result: SpeakerSearchResult
    ): Int {
        val candidateIndices = (result.lineStart..result.lineEnd)
            .filter { index -> index in lines.indices }

        if (candidateIndices.isEmpty()) return result.lineStart.coerceAtLeast(0)

        return candidateIndices
            .maxByOrNull { index ->
                lexicalSimilarity(result.matchedText, lines[index])
            }
            ?: candidateIndices.first()
    }
}
