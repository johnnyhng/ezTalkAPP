package tw.com.johnnyhng.eztalk.asr.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class WavUtilWrapperHelpersTest {
    @Test
    fun buildTranscriptFileTargetsBuildsExpectedUserScopedPaths() {
        val filesDir = File("/tmp/app-files")

        val targets = buildTranscriptFileTargets(
            filesDir = filesDir,
            userId = "user-a",
            filename = "clip-1"
        )

        assertEquals("/tmp/app-files/wavs/user-a", targets.dir.path)
        assertEquals("/tmp/app-files/wavs/user-a/clip-1.wav", targets.wavFile.path)
        assertEquals("/tmp/app-files/wavs/user-a/clip-1.jsonl", targets.jsonlFile.path)
    }

    @Test
    fun buildDeleteTranscriptPlanPairsWavAndJsonlPaths() {
        val plan = buildDeleteTranscriptPlan("/tmp/app-files/wavs/user-a/clip-1.wav")

        assertEquals("/tmp/app-files/wavs/user-a/clip-1.wav", plan.wavFile.path)
        assertEquals("/tmp/app-files/wavs/user-a/clip-1.jsonl", plan.jsonlFile.path)
    }
}
