package tw.com.johnnyhng.eztalk.asr.screens

import kotlin.math.sqrt

internal class SpeakerSemanticSearch(
    private val embeddingEngine: SpeakerEmbeddingEngine? = null,
    private val config: SpeakerSemanticSearchConfig = SpeakerSemanticSearchConfig()
) {
    fun buildQuery(text: String): SpeakerSemanticQuery {
        return SpeakerSemanticQuery(
            text = text,
            embedding = embeddingEngine?.embed(text).orEmpty()
        )
    }

    fun search(
        queryText: String,
        chunks: List<SpeakerIndexedChunk>
    ): SpeakerSearchResult? {
        val query = buildQuery(queryText)
        return search(query, chunks)
    }

    fun search(
        query: SpeakerSemanticQuery,
        chunks: List<SpeakerIndexedChunk>
    ): SpeakerSearchResult? {
        if (query.text.isBlank() || chunks.isEmpty()) return null

        return rank(query, chunks)
            .maxByOrNull { it.finalScore }
            ?.takeIf { it.finalScore >= config.minimumScoreThreshold }
    }

    fun rank(
        queryText: String,
        chunks: List<SpeakerIndexedChunk>
    ): List<SpeakerSearchResult> {
        val query = buildQuery(queryText)
        return rank(query, chunks)
    }

    fun rank(
        query: SpeakerSemanticQuery,
        chunks: List<SpeakerIndexedChunk>
    ): List<SpeakerSearchResult> {
        if (query.text.isBlank() || chunks.isEmpty()) return emptyList()

        return chunks
            .map { chunk ->
                val semanticScore = cosineSimilarity(query.embedding, chunk.embedding)
                val lexicalScore = lexicalSimilarity(query.text, chunk.text)
                val finalScore = computeFinalScore(
                    queryEmbedding = query.embedding,
                    chunkEmbedding = chunk.embedding,
                    semanticScore = semanticScore,
                    lexicalScore = lexicalScore
                )

                SpeakerSearchResult(
                    documentId = chunk.documentId,
                    lineStart = chunk.lineStart,
                    lineEnd = chunk.lineEnd,
                    matchedText = chunk.text,
                    semanticScore = semanticScore,
                    lexicalScore = lexicalScore,
                    finalScore = finalScore
                )
            }
            .sortedByDescending { it.finalScore }
    }

    private fun computeFinalScore(
        queryEmbedding: FloatArray,
        chunkEmbedding: FloatArray,
        semanticScore: Float,
        lexicalScore: Float
    ): Float {
        val hasEmbeddings =
            queryEmbedding.isNotEmpty() &&
                chunkEmbedding.isNotEmpty() &&
                queryEmbedding.size == chunkEmbedding.size

        return if (hasEmbeddings) {
            (semanticScore * config.semanticWeight) +
                (lexicalScore * config.lexicalWeight)
        } else {
            lexicalScore
        }
    }
}

internal fun cosineSimilarity(left: FloatArray, right: FloatArray): Float {
    if (left.isEmpty() || right.isEmpty() || left.size != right.size) return 0f

    var dot = 0f
    var leftNorm = 0f
    var rightNorm = 0f
    for (index in left.indices) {
        dot += left[index] * right[index]
        leftNorm += left[index] * left[index]
        rightNorm += right[index] * right[index]
    }

    if (leftNorm == 0f || rightNorm == 0f) return 0f
    return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).coerceIn(-1f, 1f)
}

internal fun lexicalSimilarity(queryText: String, candidateText: String): Float {
    val queryGrams = buildCharacterBigrams(normalizeForSearch(queryText))
    val candidateGrams = buildCharacterBigrams(normalizeForSearch(candidateText))

    if (queryGrams.isEmpty() || candidateGrams.isEmpty()) return 0f

    val intersection = queryGrams.intersect(candidateGrams).size.toFloat()
    val union = queryGrams.union(candidateGrams).size.toFloat()
    if (union == 0f) return 0f

    return (intersection / union).coerceIn(0f, 1f)
}

private fun normalizeForSearch(text: String): String {
    return buildString(text.length) {
        text.lowercase().forEach { ch ->
            if (ch.isLetterOrDigit()) {
                append(ch)
            }
        }
    }
}

private fun buildCharacterBigrams(text: String): Set<String> {
    if (text.length < 2) return if (text.isBlank()) emptySet() else setOf(text)

    val grams = mutableSetOf<String>()
    for (index in 0 until text.length - 1) {
        grams += text.substring(index, index + 2)
    }
    return grams
}

private fun FloatArray?.orEmpty(): FloatArray = this ?: floatArrayOf()
