package tw.com.johnnyhng.eztalk.asr.speaker

internal class SpeakerSemanticModule(
    private val semanticSearch: SpeakerSemanticSearch = SpeakerSemanticSearch(),
    private val config: SpeakerSemanticSearchConfig = SpeakerSemanticSearchConfig()
) {
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
