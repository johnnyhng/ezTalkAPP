package tw.com.johnnyhng.eztalk.asr.speaker

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

internal sealed interface SpeakerSemanticDecision {
    data class AutoPlay(
        val lineIndex: Int,
        val result: SpeakerSearchResult
    ) : SpeakerSemanticDecision

    data class Candidate(
        val lineIndex: Int,
        val result: SpeakerSearchResult
    ) : SpeakerSemanticDecision

    data class Ambiguous(
        val candidates: List<SpeakerSearchResult>
    ) : SpeakerSemanticDecision

    data object NoMatch : SpeakerSemanticDecision
}

internal data class SpeakerSemanticResolution(
    val query: SpeakerSemanticQuery,
    val rankedResults: List<SpeakerSearchResult>,
    val decision: SpeakerSemanticDecision
)

internal data class SpeakerLlmSemanticPayload(
    val decision: String,
    val lineStart: Int? = null,
    val lineEnd: Int? = null,
    val reason: String? = null
)

internal sealed interface SpeakerLlmFallbackState {
    data class PreviewReady(
        val model: String,
        val candidateCount: Int
    ) : SpeakerLlmFallbackState

    data class Success(
        val decision: SpeakerSemanticDecision
    ) : SpeakerLlmFallbackState

    data class Failure(
        val message: String
    ) : SpeakerLlmFallbackState

    data object Unavailable : SpeakerLlmFallbackState
}

internal data class SpeakerNoMatchOutcome(
    val isLlmFallbackEnabled: Boolean = false,
    val llmRequestModel: String? = null,
    val llmCandidateCount: Int = 0,
    val llmFallbackResult: Result<SpeakerSemanticDecision>? = null
)
