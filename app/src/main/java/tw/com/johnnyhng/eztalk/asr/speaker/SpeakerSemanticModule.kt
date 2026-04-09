package tw.com.johnnyhng.eztalk.asr.speaker

import org.json.JSONException
import org.json.JSONObject
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

    suspend fun tryLlmFallback(
        queryText: String,
        rankedResults: List<SpeakerSearchResult>,
        lines: List<String>,
        maxCandidates: Int = 5
    ): Result<SpeakerSemanticDecision> {
        val provider = llmProvider ?: return Result.failure(
            IllegalStateException("LLM provider is not configured")
        )

        val request = buildLlmRequest(
            queryText = queryText,
            rankedResults = rankedResults,
            maxCandidates = maxCandidates
        ) ?: return Result.failure(
            IllegalArgumentException("Unable to build LLM request for fallback")
        )

        return provider.generate(request).map { response ->
            parseLlmResponse(
                response = response,
                rankedResults = rankedResults,
                lines = lines
            )
        }
    }

    private fun findMatchingResult(
        payload: SpeakerLlmSemanticPayload,
        rankedResults: List<SpeakerSearchResult>
    ): SpeakerSearchResult? {
        val start = payload.lineStart
        val end = payload.lineEnd
        if (start == null || end == null) return null

        return rankedResults.firstOrNull { result ->
            result.lineStart == start && result.lineEnd == end
        } ?: rankedResults.firstOrNull { result ->
            start in result.lineStart..result.lineEnd || end in result.lineStart..result.lineEnd
        }
    }

    private fun parseSemanticPayload(rawText: String): SpeakerLlmSemanticPayload? {
        val json = parseSemanticJson(rawText) ?: return null
        val decision = json.optString("decision")
            .trim()
            .lowercase()
            .takeIf { it in allowedDecisions }
            ?: return null

        return SpeakerLlmSemanticPayload(
            decision = decision,
            lineStart = json.optNullableInt("lineStart"),
            lineEnd = json.optNullableInt("lineEnd"),
            reason = json.optString("reason").takeIf { it.isNotBlank() }
        )
    }

    private fun parseSemanticJson(rawText: String): JSONObject? {
        val normalized = rawText.trim()
        if (normalized.isBlank()) return null

        val candidates = buildList {
            add(normalized)
            extractJsonFromCodeFence(normalized)?.let(::add)
        }.distinct()

        for (candidate in candidates) {
            try {
                return JSONObject(candidate)
            } catch (_: JSONException) {
                // Try the next normalized candidate.
            }
        }

        return null
    }

    private fun extractJsonFromCodeFence(text: String): String? {
        if (!text.startsWith("```")) return null

        val firstLineBreak = text.indexOf('\n')
        if (firstLineBreak == -1) return null

        val closingFenceIndex = text.lastIndexOf("```")
        if (closingFenceIndex <= firstLineBreak) return null

        return text.substring(firstLineBreak + 1, closingFenceIndex).trim()
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
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

    private companion object {
        val allowedDecisions = setOf(
            "match",
            "candidate",
            "autoplay",
            "play",
            "ambiguous",
            "no_match"
        )
    }
}
