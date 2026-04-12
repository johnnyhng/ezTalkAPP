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

    @Test
    fun appendTranslateSamplesStartsSpeechAtBufferedLeadInWhenVadFirstDetectsSpeech() {
        val state = TranslateCaptureState(
            fullRecordingBuffer = arrayListOf(0.1f, 0.2f, 0.3f)
        )

        appendTranslateSamples(
            state = state,
            samples = floatArrayOf(0.4f, 0.5f),
            keepSamples = 2,
            speechDetected = true
        )

        assertTrue(state.isSpeechStarted)
        assertEquals(1, state.speechStartOffset)
        assertEquals(listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f), state.fullRecordingBuffer)
    }

    @Test
    fun buildTranslateFinalAudioUsesVadTailPaddingAndReturnsSingleTrimmedSegment() {
        val state = TranslateCaptureState(
            fullRecordingBuffer = arrayListOf(0f, 1f, 2f, 3f, 4f, 5f),
            speechStartOffset = 1,
            lastSpeechDetectedOffset = 4,
            isSpeechStarted = true
        )

        val audio = buildTranslateFinalAudio(
            state = state,
            keepSamples = 1
        )

        assertEquals(listOf(1f, 2f, 3f, 4f), audio.toList())
    }

    @Test
    fun buildTranslateFinalAudioReturnsEmptyWhenSpeechNeverStarted() {
        val audio = buildTranslateFinalAudio(
            state = TranslateCaptureState(
                fullRecordingBuffer = arrayListOf(1f, 2f, 3f)
            ),
            keepSamples = 3
        )

        assertTrue(audio.isEmpty())
    }

    @Test
    fun submitTranslateFeedbackWaitsForFetchAndReturnsFeedbackUpdatedTranscript() = runBlocking {
        val transcript = Transcript(
            recognizedText = "orig",
            modifiedText = "orig",
            wavFilePath = "/tmp/sample.wav",
            localCandidates = listOf("local-1")
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fetchJob = async {
            started.complete(Unit)
            release.await()
        }

        started.await()

        val submission = async {
            submitTranslateFeedback(
                transcript = transcript,
                newText = "confirmed",
                enableTtsFeedback = true,
                remoteCandidates = listOf("remote-1"),
                fetchJob = fetchJob,
                feedbackBlock = { true }
            )
        }

        delay(50)
        assertFalse(submission.isCompleted)

        release.complete(Unit)
        val result = submission.await() as TranslateFeedbackSubmission.Success

        assertEquals("confirmed", result.transcript.modifiedText)
        assertEquals(listOf("remote-1"), result.transcript.remoteCandidates)
        assertTrue(result.transcript.checked)
    }

    @Test
    fun submitTranslateFeedbackSkipsBackendWhenFeedbackIsDisabled() = runBlocking {
        val transcript = Transcript(
            recognizedText = "orig",
            modifiedText = "orig",
            wavFilePath = "/tmp/sample.wav",
            localCandidates = listOf("local-1"),
            remoteCandidates = listOf("existing-remote")
        )
        var feedbackCalls = 0

        val result = submitTranslateFeedback(
            transcript = transcript,
            newText = "edited",
            enableTtsFeedback = false,
            remoteCandidates = listOf("new-remote"),
            fetchJob = null,
            feedbackBlock = {
                feedbackCalls += 1
                true
            }
        ) as TranslateFeedbackSubmission.Success

        assertEquals(0, feedbackCalls)
        assertEquals("edited", result.transcript.modifiedText)
        assertEquals(listOf("existing-remote"), result.transcript.remoteCandidates)
        assertTrue(result.transcript.checked)
        assertTrue(result.transcript.mutable)
    }

    @Test
    fun submitTranslateFeedbackReturnsFailedWhenBackendFeedbackFails() = runBlocking {
        val transcript = Transcript(
            recognizedText = "orig",
            modifiedText = "orig",
            wavFilePath = "/tmp/sample.wav"
        )

        val result = submitTranslateFeedback(
            transcript = transcript,
            newText = "edited",
            enableTtsFeedback = true,
            remoteCandidates = emptyList(),
            fetchJob = null,
            feedbackBlock = { false }
        )

        assertTrue(result is TranslateFeedbackSubmission.Failed)
    }
}
