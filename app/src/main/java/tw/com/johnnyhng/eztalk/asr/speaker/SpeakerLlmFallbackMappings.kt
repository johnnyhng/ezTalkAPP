package tw.com.johnnyhng.eztalk.asr.speaker

import tw.com.johnnyhng.eztalk.asr.R

internal fun SpeakerNoMatchOutcome.toFallbackState(
    fallbackMessage: String,
    onFailure: (Throwable?) -> Unit = {}
): SpeakerLlmFallbackState? {
    return when {
        llmFallbackResult?.isSuccess == true -> SpeakerLlmFallbackState.Success(
            llmFallbackResult.getOrThrow()
        )
        llmFallbackResult?.isFailure == true -> {
            val error = llmFallbackResult.exceptionOrNull()
            onFailure(error)
            SpeakerLlmFallbackState.Failure(
                error.toDisplayMessage(fallback = fallbackMessage)
            )
        }
        llmRequestModel != null -> SpeakerLlmFallbackState.PreviewReady(
            model = llmRequestModel,
            candidateCount = llmCandidateCount
        )
        isLlmFallbackEnabled -> SpeakerLlmFallbackState.Unavailable
        else -> null
    }
}

internal fun SpeakerNoMatchOutcome.toastMessageResId(): Int {
    return when {
        llmFallbackResult?.isSuccess == true -> R.string.speaker_semantic_no_match_llm_applied
        llmRequestModel != null -> R.string.speaker_semantic_no_match_llm_preview
        else -> R.string.speaker_semantic_no_match
    }
}

private fun Throwable?.toDisplayMessage(fallback: String): String {
    if (this == null) return fallback

    val parts = buildList {
        message?.takeIf { it.isNotBlank() }?.let(::add)
        cause?.message?.takeIf { it.isNotBlank() && it != message }?.let(::add)
    }

    return parts.joinToString(" | ").ifBlank { fallback }
}
