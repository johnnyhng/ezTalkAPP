package tw.com.johnnyhng.eztalk.asr.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures

class TranscriptWorkflowTest {
    @Test
    fun shouldAttemptFeedbackReturnsTrueWhenEnabledAndTranscriptIsNotYetRemovable() {
        val transcript = TestFixtures.transcript(removable = false)

        val result = shouldAttemptFeedback(
            transcript = transcript,
            enableTtsFeedback = true
        )

        assertTrue(result)
    }

    @Test
    fun shouldAttemptFeedbackReturnsFalseWhenTranscriptIsAlreadyRemovable() {
        val transcript = TestFixtures.transcript(removable = true)

        val result = shouldAttemptFeedback(
            transcript = transcript,
            enableTtsFeedback = true
        )

        assertFalse(result)
    }

    @Test
    fun shouldAttemptFeedbackReturnsFalseWhenFeatureIsDisabled() {
        val transcript = TestFixtures.transcript(removable = false)

        val result = shouldAttemptFeedback(
            transcript = transcript,
            enableTtsFeedback = false
        )

        assertFalse(result)
    }

    @Test
    fun reduceTranscriptAfterConfirmationWithoutLockOnlyMarksCheckedAndUpdatesText() {
        val transcript = TestFixtures.transcript(
            modifiedText = "before",
            checked = false,
            mutable = true,
            removable = false
        )

        val result = reduceTranscriptAfterConfirmation(
            transcript = transcript,
            newText = "after",
            lockTranscript = false
        )

        assertEquals("after", result.modifiedText)
        assertTrue(result.checked)
        assertTrue(result.mutable)
        assertFalse(result.removable)
    }

    @Test
    fun reduceTranscriptAfterConfirmationWithLockMarksCheckedAndLocksTranscript() {
        val transcript = TestFixtures.transcript(
            modifiedText = "before",
            checked = false,
            mutable = true,
            removable = false
        )

        val result = reduceTranscriptAfterConfirmation(
            transcript = transcript,
            newText = "after",
            lockTranscript = true
        )

        assertEquals("after", result.modifiedText)
        assertTrue(result.checked)
        assertFalse(result.mutable)
        assertTrue(result.removable)
    }
}
