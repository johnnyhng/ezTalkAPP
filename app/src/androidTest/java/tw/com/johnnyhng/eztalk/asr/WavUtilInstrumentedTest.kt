package tw.com.johnnyhng.eztalk.asr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tw.com.johnnyhng.eztalk.asr.utils.readJsonl
import tw.com.johnnyhng.eztalk.asr.utils.saveJsonl
import java.io.File

@RunWith(AndroidJUnit4::class)
class WavUtilInstrumentedTest {
    @Test
    fun saveJsonlAndReadJsonlRoundTripMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userId = "instrumented_storage_user"
        val filename = "roundtrip"

        val path = saveJsonl(
            context = context,
            userId = userId,
            filename = filename,
            originalText = "original",
            modifiedText = "modified",
            checked = true,
            mutable = false,
            removable = true,
            localCandidates = listOf("local-1"),
            remoteCandidates = listOf("remote-1")
        )

        assertNotNull(path)
        val file = File(context.filesDir, "wavs/$userId/$filename.jsonl")
        assertTrue(file.exists())

        val json = readJsonl(file.absolutePath)
        assertNotNull(json)
        assertEquals("original", json?.getString("original"))
        assertEquals("modified", json?.getString("modified"))
        assertEquals(true, json?.getBoolean("checked"))
        assertEquals(false, json?.getBoolean("mutable"))
        assertEquals(true, json?.getBoolean("removable"))
        assertEquals("local-1", json?.getJSONArray("local_candidates")?.getString(0))
        assertEquals("remote-1", json?.getJSONArray("remote_candidates")?.getString(0))
    }
}
