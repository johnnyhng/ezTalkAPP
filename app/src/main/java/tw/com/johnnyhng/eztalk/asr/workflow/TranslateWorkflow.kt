package tw.com.johnnyhng.eztalk.asr.workflow

import kotlinx.coroutines.Job

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
