package tw.com.johnnyhng.eztalk.asr.speaker

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.llm.LlmProvider
import tw.com.johnnyhng.eztalk.asr.llm.LlmRequest
import tw.com.johnnyhng.eztalk.asr.llm.LlmResponse
import tw.com.johnnyhng.eztalk.asr.prompt.SpeakerCommandPromptLine
import tw.com.johnnyhng.eztalk.asr.prompt.SpeakerCommandResolutionPromptBuilder
import tw.com.johnnyhng.eztalk.asr.prompt.SpeakerSemanticPromptBuilder
import tw.com.johnnyhng.eztalk.asr.prompt.SpeakerSemanticPromptCandidate

internal class SpeakerSemanticModule(
    private val semanticSearch: SpeakerSemanticSearch = SpeakerSemanticSearch(),
    private val config: SpeakerSemanticSearchConfig = SpeakerSemanticSearchConfig(),
    private val llmProvider: LlmProvider? = null,
    private val promptBuilder: SpeakerSemanticPromptBuilder = SpeakerSemanticPromptBuilder(),
    private val commandPromptBuilder: SpeakerCommandResolutionPromptBuilder = SpeakerCommandResolutionPromptBuilder(),
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
                result = bestResult,
                source = SpeakerDecisionSource.SEMANTIC
            )
        } else {
            SpeakerSemanticDecision.Candidate(
                lineIndex = matchedLineIndex,
                result = bestResult,
                source = SpeakerDecisionSource.SEMANTIC
            )
        }

        return SpeakerSemanticResolution(
            query = query,
            rankedResults = rankedResults,
            decision = decision
        )
    }

    fun buildLlmRequest(
        utterance: SpeakerAsrUtteranceBundle,
        rankedResults: List<SpeakerSearchResult>,
        lines: List<String>,
        maxCandidates: Int = 5
    ): LlmRequest? {
        if (utterance.variants.isEmpty()) return null

        val prompt = if (lines.isNotEmpty()) {
            commandPromptBuilder.build(
                utteranceVariants = utterance.variants,
                commandOptions = listOf("play_document", "play_line", "pause", "stop", "no_action"),
                lines = lines.mapIndexed { index, text ->
                    SpeakerCommandPromptLine(
                        lineIndex = index,
                        text = text
                    )
                }
            )
        } else {
            if (rankedResults.isEmpty()) return null
            promptBuilder.build(
                asrText = utterance.primaryText,
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
        }

        return LlmRequest(
            model = llmModel,
            systemInstruction = prompt.systemInstruction,
            userPrompt = prompt.userPrompt,
            outputFormat = tw.com.johnnyhng.eztalk.asr.llm.LlmOutputFormat.JSON,
            schemaHint = prompt.expectedResponseSchema
        ).also {
            safeLog(
                "Speaker LLM request built variants=${utterance.variants.size} lines=${lines.size} rankedCandidates=${rankedResults.take(maxCandidates).size} model=${it.model}"
            )
        }
    }

    fun parseLlmResponse(
        response: LlmResponse,
        rankedResults: List<SpeakerSearchResult>,
        lines: List<String>
    ): SpeakerSemanticDecision {
        val payload = parseSemanticPayload(response.rawText) ?: return SpeakerSemanticDecision.NoMatch
        safeLog(
            "Speaker LLM payload parsed action=${payload.action.orEmpty()} decision=${payload.decision.orEmpty()} lineIndex=${payload.lineIndex} lineRange=${payload.lineStart}-${payload.lineEnd} confidence=${payload.confidence ?: 0f} reason=${payload.reason.orEmpty()}"
        )

        when (payload.action) {
            "play_line" -> {
                val confidence = payload.confidence ?: 0f
                if (confidence < llmCandidateConfidenceThreshold) return SpeakerSemanticDecision.NoMatch
                val lineIndex = payload.lineIndex?.takeIf { it in lines.indices }
                    ?: return SpeakerSemanticDecision.NoMatch
                val result = resultForLineIndex(lines, rankedResults, lineIndex)
                return if (confidence >= llmAutoplayConfidenceThreshold) {
                    SpeakerSemanticDecision.AutoPlay(
                        lineIndex = lineIndex,
                        result = result,
                        source = SpeakerDecisionSource.LLM
                    )
                } else {
                    SpeakerSemanticDecision.Candidate(
                        lineIndex = lineIndex,
                        result = result,
                        source = SpeakerDecisionSource.LLM
                    )
                }
            }

            "play_document" -> {
                val confidence = payload.confidence ?: 0f
                if (confidence < llmCommandConfidenceThreshold) return SpeakerSemanticDecision.NoMatch
                return SpeakerSemanticDecision.Command(
                    command = SpeakerContentCommand.Play,
                    source = SpeakerDecisionSource.LLM,
                    confidence = confidence,
                    reason = payload.reason
                )
            }

            "pause" -> {
                val confidence = payload.confidence ?: 0f
                return if (confidence >= llmCommandConfidenceThreshold) {
                    SpeakerSemanticDecision.Command(
                        command = SpeakerContentCommand.Pause,
                        source = SpeakerDecisionSource.LLM,
                        confidence = confidence,
                        reason = payload.reason
                    )
                } else {
                    SpeakerSemanticDecision.NoMatch
                }
            }

            "stop" -> {
                val confidence = payload.confidence ?: 0f
                return if (confidence >= llmCommandConfidenceThreshold) {
                    SpeakerSemanticDecision.Command(
                        command = SpeakerContentCommand.Stop,
                        source = SpeakerDecisionSource.LLM,
                        confidence = confidence,
                        reason = payload.reason
                    )
                } else {
                    SpeakerSemanticDecision.NoMatch
                }
            }

            "no_action" -> {
                return SpeakerSemanticDecision.NoMatch
            }
        }

        return when (payload.decision?.lowercase()) {
            "match", "candidate" -> {
                val matched = findMatchingResult(payload, rankedResults)
                if (matched == null) {
                    SpeakerSemanticDecision.NoMatch
                } else {
                    SpeakerSemanticDecision.Candidate(
                        lineIndex = resolveMatchedLineIndex(lines, matched),
                        result = matched,
                        source = SpeakerDecisionSource.LLM
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
                        result = matched,
                        source = SpeakerDecisionSource.LLM
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
        utterance: SpeakerAsrUtteranceBundle,
        rankedResults: List<SpeakerSearchResult>,
        lines: List<String>,
        maxCandidates: Int = 5
    ): Result<SpeakerSemanticDecision> {
        val provider = llmProvider ?: return Result.failure(
            IllegalStateException("LLM provider is not configured")
        )

        val request = buildLlmRequest(
            utterance = utterance,
            rankedResults = rankedResults,
            lines = lines,
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

    suspend fun resolveNoMatchOutcome(
        utterance: SpeakerAsrUtteranceBundle,
        rankedResults: List<SpeakerSearchResult>,
        lines: List<String>,
        isLlmFallbackEnabled: Boolean,
        maxCandidates: Int = 5
    ): SpeakerNoMatchOutcome {
        val llmRequest = buildLlmRequest(
            utterance = utterance,
            rankedResults = rankedResults,
            lines = lines,
            maxCandidates = maxCandidates
        )
        val llmFallbackResult = if (isLlmFallbackEnabled) {
            tryLlmFallback(
                utterance = utterance,
                rankedResults = rankedResults,
                lines = lines,
                maxCandidates = maxCandidates
            )
        } else {
            null
        }

        return SpeakerNoMatchOutcome(
            isLlmFallbackEnabled = isLlmFallbackEnabled,
            llmRequestModel = llmRequest?.model,
            llmCandidateCount = rankedResults.take(maxCandidates).size,
            llmFallbackResult = llmFallbackResult
        )
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
        val action = json.optString("action")
            .trim()
            .lowercase()
            .takeIf { it in allowedActions }
        if (decision == null && action == null) return null

        return SpeakerLlmSemanticPayload(
            decision = decision,
            action = action,
            confidence = json.optNullableDouble("confidence")?.toFloat(),
            lineIndex = json.optNullableInt("lineIndex"),
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

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key)
    }

    private fun resultForLineIndex(
        lines: List<String>,
        rankedResults: List<SpeakerSearchResult>,
        lineIndex: Int
    ): SpeakerSearchResult {
        return rankedResults.firstOrNull { lineIndex in it.lineStart..it.lineEnd }
            ?: SpeakerSearchResult(
                documentId = rankedResults.firstOrNull()?.documentId ?: "",
                lineStart = lineIndex,
                lineEnd = lineIndex,
                matchedText = lines.getOrNull(lineIndex).orEmpty(),
                semanticScore = 0f,
                lexicalScore = 0f,
                finalScore = 0f
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

    private fun safeLog(message: String) {
        runCatching { Log.i(TAG, message) }
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

        val allowedActions = setOf(
            "play_document",
            "play_line",
            "pause",
            "stop",
            "no_action"
        )

        const val llmCandidateConfidenceThreshold = 0.55f
        const val llmAutoplayConfidenceThreshold = 0.82f
        const val llmCommandConfidenceThreshold = 0.80f
    }
}
