package tw.com.johnnyhng.eztalk.asr.utils

import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionUtilsGetRemoteCandidatesTest {
    @Test
    fun getRemoteCandidatesReturnsEmptyWhenRecognitionUrlIsBlank() = runBlocking {
        val result = getRemoteCandidates(
            context = ContextWrapper(null),
            wavFilePath = "/tmp/sample.wav",
            userId = "tester@example.com",
            recognitionUrl = "",
            originalText = "original",
            currentText = "current"
        )

        assertEquals(emptyList<String>(), result)
    }
}
