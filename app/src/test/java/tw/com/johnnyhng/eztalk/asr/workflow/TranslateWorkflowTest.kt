package tw.com.johnnyhng.eztalk.asr.workflow

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateWorkflowTest {

    @Test
    fun shouldCompleteTranslateCaptureOnlyWhenStopIsRequestedOrChannelEndsWithoutPendingSample() {
        assertFalse(
            shouldCompleteTranslateCapture(
                hasSample = true,
                flushRequested = false,
                samplesChannelClosed = false
            )
        )
        assertFalse(
            shouldCompleteTranslateCapture(
                hasSample = false,
                flushRequested = false,
                samplesChannelClosed = false
            )
        )
        assertFalse(
            shouldCompleteTranslateCapture(
                hasSample = true,
                flushRequested = false,
                samplesChannelClosed = true
            )
        )
        assertTrue(
            shouldCompleteTranslateCapture(
                hasSample = false,
                flushRequested = true,
                samplesChannelClosed = false
            )
        )
        assertTrue(
            shouldCompleteTranslateCapture(
                hasSample = false,
                flushRequested = false,
                samplesChannelClosed = true
            )
        )
    }

    @Test
    fun awaitCandidateFetchBeforeFeedbackWaitsForFetchJobCompletion() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fetchJob: Job = async {
            started.complete(Unit)
            release.await()
        }

        started.await()

        val waiter = async {
            awaitCandidateFetchBeforeFeedback(fetchJob)
        }

        delay(50)
        assertFalse(waiter.isCompleted)

        release.complete(Unit)
        waiter.await()
        assertTrue(waiter.isCompleted)
    }
}
