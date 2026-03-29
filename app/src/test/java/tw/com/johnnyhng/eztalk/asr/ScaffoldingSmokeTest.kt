package tw.com.johnnyhng.eztalk.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures

class ScaffoldingSmokeTest {
    @Test
    fun transcriptFixtureProvidesAppLevelDefaults() {
        val transcript = TestFixtures.transcript(
            localCandidates = listOf("local-1"),
            remoteCandidates = listOf("remote-1")
        )

        assertEquals("recognized", transcript.recognizedText)
        assertEquals("recognized", transcript.modifiedText)
        assertEquals(listOf("local-1"), transcript.localCandidates)
        assertEquals(listOf("remote-1"), transcript.remoteCandidates)
        assertTrue(transcript.mutable)
        assertEquals("/tmp/sample.wav", transcript.wavFilePath)
    }

    @Test
    fun tempDirFixtureCreatesWritableDirectory() {
        val dir = TestFixtures.tempDir()

        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
        assertTrue(dir.canWrite())
    }
}
