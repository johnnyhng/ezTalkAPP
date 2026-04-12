package tw.com.johnnyhng.eztalk.asr.workflow

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript

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

    @Test
    fun createTranslateTranscriptSeedsModifiedTextAndLocalCandidates() {
        val transcript = createTranslateTranscript(
            recognizedText = "hello",
            wavFilePath = "/tmp/sample.wav"
        )

        assertEquals("hello", transcript.recognizedText)
        assertEquals("hello", transcript.modifiedText)
        assertEquals("/tmp/sample.wav", transcript.wavFilePath)
        assertEquals(listOf("hello"), transcript.localCandidates)
        assertTrue(transcript.remoteCandidates.isEmpty())
    }

    @Test
    fun applyTranslateFeedbackResultPreservesLocalCandidatesAndReplacesRemoteCandidates() {
        val transcript = Transcript(
            recognizedText = "orig",
            modifiedText = "orig",
            wavFilePath = "/tmp/sample.wav",
            localCandidates = listOf("local-1"),
            remoteCandidates = listOf("old-remote")
        )

        val updated = applyTranslateFeedbackResult(
            transcript = transcript,
            newText = "confirmed",
            lockTranscript = true,
            remoteCandidates = listOf("remote-1", "remote-2")
        )

        assertEquals("confirmed", updated.modifiedText)
        assertEquals(listOf("local-1"), updated.localCandidates)
        assertEquals(listOf("remote-1", "remote-2"), updated.remoteCandidates)
        assertTrue(updated.checked)
        assertFalse(updated.mutable)
        assertTrue(updated.removable)
    }
}
