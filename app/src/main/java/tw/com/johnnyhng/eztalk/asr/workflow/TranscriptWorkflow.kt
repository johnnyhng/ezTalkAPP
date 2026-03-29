package tw.com.johnnyhng.eztalk.asr.workflow

import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript

internal fun shouldAttemptFeedback(
    transcript: Transcript,
    enableTtsFeedback: Boolean
): Boolean = enableTtsFeedback && !transcript.removable

internal fun reduceTranscriptAfterConfirmation(
    transcript: Transcript,
    newText: String,
    lockTranscript: Boolean
): Transcript {
    return if (lockTranscript) {
        transcript.copy(
            modifiedText = newText,
            checked = true,
            mutable = false,
            removable = true
        )
    } else {
        transcript.copy(
            modifiedText = newText,
            checked = true
        )
    }
}
