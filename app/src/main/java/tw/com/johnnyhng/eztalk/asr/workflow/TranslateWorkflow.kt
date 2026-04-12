package tw.com.johnnyhng.eztalk.asr.workflow

import kotlinx.coroutines.Job
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript

internal sealed interface TranslateFeedbackSubmission {
    data class Success(
        val transcript: Transcript,
        val sentBackendFeedback: Boolean
    ) : TranslateFeedbackSubmission
    data object Skipped : TranslateFeedbackSubmission
    data object Failed : TranslateFeedbackSubmission
}

internal fun shouldCompleteTranslateCapture(
    hasSample: Boolean,
    flushRequested: Boolean,
    samplesChannelClosed: Boolean
): Boolean {
    return flushRequested || (samplesChannelClosed && !hasSample)
}

internal suspend fun awaitCandidateFetchBeforeFeedback(fetchJob: Job?) {
    fetchJob?.join()
}

internal fun createTranslateTranscript(
    recognizedText: String,
    wavFilePath: String
): Transcript {
    return Transcript(
        recognizedText = recognizedText,
        wavFilePath = wavFilePath,
        modifiedText = recognizedText,
        localCandidates = listOf(recognizedText)
    )
}

internal fun applyTranslateFeedbackResult(
    transcript: Transcript,
    newText: String,
    lockTranscript: Boolean,
    remoteCandidates: List<String>
): Transcript {
    return reduceTranscriptAfterConfirmation(
        transcript = transcript,
        newText = newText,
        lockTranscript = lockTranscript
    ).copy(
        localCandidates = transcript.localCandidates,
        remoteCandidates = remoteCandidates
    )
}

internal suspend fun submitTranslateFeedback(
    transcript: Transcript,
    newText: String,
    enableTtsFeedback: Boolean,
    remoteCandidates: List<String>,
    fetchJob: Job?,
    awaitFetchBlock: (suspend (Job?) -> Unit)? = null,
    feedbackBlock: suspend (Transcript) -> Boolean
): TranslateFeedbackSubmission {
    if (!shouldAttemptFeedback(transcript, enableTtsFeedback)) {
        return TranslateFeedbackSubmission.Success(
            transcript = applyTranslateFeedbackResult(
                transcript = transcript,
                newText = newText,
                lockTranscript = enableTtsFeedback,
                remoteCandidates = transcript.remoteCandidates
            ),
            sentBackendFeedback = false
        )
    }

    val awaitFetch = awaitFetchBlock ?: ::awaitCandidateFetchBeforeFeedback
    awaitFetch(fetchJob)
    val success = feedbackBlock(transcript)
    if (!success) {
        return TranslateFeedbackSubmission.Failed
    }

    return TranslateFeedbackSubmission.Success(
        transcript = applyTranslateFeedbackResult(
            transcript = transcript,
            newText = newText,
            lockTranscript = true,
            remoteCandidates = remoteCandidates
        ),
        sentBackendFeedback = true
    )
}
