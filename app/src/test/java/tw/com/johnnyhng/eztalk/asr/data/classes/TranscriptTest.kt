package tw.com.johnnyhng.eztalk.asr.data.classes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptTest {
    @Test
    fun constructorUsesRecognizedTextAsDefaultModifiedText() {
        val transcript = Transcript(
            recognizedText = "hello world",
            wavFilePath = "/tmp/sample.wav"
        )

        assertEquals("hello world", transcript.recognizedText)
        assertEquals("hello world", transcript.modifiedText)
        assertEquals("/tmp/sample.wav", transcript.wavFilePath)
    }

    @Test
    fun constructorUsesExpectedWorkflowDefaults() {
        val transcript = Transcript(
            recognizedText = "hello world",
            wavFilePath = "/tmp/sample.wav"
        )

        assertFalse(transcript.checked)
        assertTrue(transcript.mutable)
        assertFalse(transcript.removable)
        assertTrue(transcript.localCandidates.isEmpty())
        assertTrue(transcript.remoteCandidates.isEmpty())
    }

    @Test
    fun copyAllowsIndependentWorkflowAndCandidateUpdates() {
        val original = Transcript(
            recognizedText = "recognized",
            modifiedText = "modified",
            wavFilePath = "/tmp/sample.wav",
            checked = false,
            mutable = true,
            removable = false,
            localCandidates = listOf("local-1"),
            remoteCandidates = listOf("remote-1")
        )

        val updated = original.copy(
            modifiedText = "confirmed",
            checked = true,
            mutable = false,
            removable = true,
            localCandidates = listOf("local-1", "local-2"),
            remoteCandidates = listOf("remote-1", "remote-2")
        )

        assertEquals("recognized", updated.recognizedText)
        assertEquals("confirmed", updated.modifiedText)
        assertTrue(updated.checked)
        assertFalse(updated.mutable)
        assertTrue(updated.removable)
        assertEquals(listOf("local-1", "local-2"), updated.localCandidates)
        assertEquals(listOf("remote-1", "remote-2"), updated.remoteCandidates)

        assertEquals("modified", original.modifiedText)
        assertFalse(original.checked)
        assertTrue(original.mutable)
        assertFalse(original.removable)
        assertEquals(listOf("local-1"), original.localCandidates)
        assertEquals(listOf("remote-1"), original.remoteCandidates)
    }
}
