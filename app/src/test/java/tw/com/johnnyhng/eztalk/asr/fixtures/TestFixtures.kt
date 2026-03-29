package tw.com.johnnyhng.eztalk.asr.fixtures

import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import java.io.File
import java.nio.file.Files

object TestFixtures {
    fun transcript(
        recognizedText: String = "recognized",
        modifiedText: String = recognizedText,
        wavFilePath: String = "/tmp/sample.wav",
        checked: Boolean = false,
        mutable: Boolean = true,
        removable: Boolean = false,
        localCandidates: List<String> = emptyList(),
        remoteCandidates: List<String> = emptyList()
    ): Transcript = Transcript(
        recognizedText = recognizedText,
        modifiedText = modifiedText,
        wavFilePath = wavFilePath,
        checked = checked,
        mutable = mutable,
        removable = removable,
        localCandidates = localCandidates,
        remoteCandidates = remoteCandidates
    )

    fun tempDir(prefix: String = "eztalk-test"): File =
        Files.createTempDirectory(prefix).toFile().apply { deleteOnExit() }
}
