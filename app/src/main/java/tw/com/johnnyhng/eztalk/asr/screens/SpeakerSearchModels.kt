package tw.com.johnnyhng.eztalk.asr.screens

internal data class SpeakerIndexedChunk(
    val documentId: String,
    val lineStart: Int,
    val lineEnd: Int,
    val text: String,
    val embedding: FloatArray = floatArrayOf()
)

internal data class SpeakerSemanticQuery(
    val text: String,
    val embedding: FloatArray = floatArrayOf()
)

internal data class SpeakerSearchResult(
    val documentId: String,
    val lineStart: Int,
    val lineEnd: Int,
    val matchedText: String,
    val semanticScore: Float,
    val lexicalScore: Float,
    val finalScore: Float
)

internal data class SpeakerSemanticSearchConfig(
    val chunkLineWindow: Int = 3,
    val semanticWeight: Float = 0.7f,
    val lexicalWeight: Float = 0.3f,
    val minimumScoreThreshold: Float = 0.35f,
    val candidateScoreThreshold: Float = 0.45f,
    val autoPlayScoreThreshold: Float = 0.62f
)
