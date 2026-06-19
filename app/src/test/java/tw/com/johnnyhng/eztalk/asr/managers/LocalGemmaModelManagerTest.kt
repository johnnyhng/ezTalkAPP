package tw.com.johnnyhng.eztalk.asr.managers

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures
import tw.com.johnnyhng.eztalk.asr.llm.LocalGemmaModelManager
import tw.com.johnnyhng.eztalk.asr.llm.SpeakerLocalLlmStatus
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LocalGemmaModelManagerTest {
    private val context: Context = object : ContextWrapper(null) {
        private val filesDir = TestFixtures.tempDir("gemma-model-context")

        override fun getFilesDir(): File = filesDir
    }

    @Test
    fun checkReturnsDownloadableWhenModelFileDoesNotExist() {
        val manager = LocalGemmaModelManager(context)
        if (manager.modelFile.exists()) {
            manager.modelFile.delete()
        }

        val status = manager.check()
        assertEquals(SpeakerLocalLlmStatus.Downloadable, status)
    }

    @Test
    fun checkReturnsAvailableWhenModelFileExistsAndIsLarge() {
        val manager = LocalGemmaModelManager(context)
        manager.modelFile.parentFile?.mkdirs()
        manager.modelFile.writeBytes(ByteArray(105 * 1024 * 1024)) // 105 MB

        val status = manager.check()
        assertEquals(SpeakerLocalLlmStatus.Available, status)

        manager.modelFile.delete()
    }
}
