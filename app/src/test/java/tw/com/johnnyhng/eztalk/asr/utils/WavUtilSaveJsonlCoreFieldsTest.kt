package tw.com.johnnyhng.eztalk.asr.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WavUtilSaveJsonlCoreFieldsTest {
    @Test
    fun saveJsonlWritesExpectedCoreFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val userId = "batch4_user"
        val filename = "core-fields"

        val savedPath = saveJsonl(
            context = context,
            userId = userId,
            filename = filename,
            originalText = "original-text",
            modifiedText = "modified-text",
            checked = true,
            mutable = false,
            removable = true
        )

        assertNotNull(savedPath)

        val file = File(context.filesDir, "wavs/$userId/$filename.jsonl")
        assertTrue(file.exists())

        val json = JSONObject(file.readText())
        assertEquals("original-text", json.getString("original"))
        assertEquals("modified-text", json.getString("modified"))
        assertTrue(json.getBoolean("checked"))
        assertFalse(json.getBoolean("mutable"))
        assertTrue(json.getBoolean("removable"))
    }

    @Test
    fun saveJsonlCreatesUserDirectoryWhenMissing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val userId = "batch4_missing_dir_user"
        val filename = "creates-dir"
        val dir = File(context.filesDir, "wavs/$userId")
        dir.deleteRecursively()

        val savedPath = saveJsonl(
            context = context,
            userId = userId,
            filename = filename,
            originalText = "original",
            modifiedText = "modified",
            checked = false
        )

        assertNotNull(savedPath)
        assertTrue(dir.exists())
        assertTrue(File(dir, "$filename.jsonl").exists())
    }
}
