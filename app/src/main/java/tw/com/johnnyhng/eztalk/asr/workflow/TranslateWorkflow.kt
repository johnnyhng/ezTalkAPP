package tw.com.johnnyhng.eztalk.asr.workflow

import kotlinx.coroutines.Job
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript

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
