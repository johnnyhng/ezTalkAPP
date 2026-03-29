package tw.com.johnnyhng.eztalk.asr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WavUtilReadJsonlTest {
    @Test
    fun readJsonlReturnsNullWhenFileDoesNotExist() {
        val dir = TestFixtures.tempDir("missing-jsonl")
        val missingFile = File(dir, "missing.jsonl")

        val result = readJsonl(missingFile.absolutePath)

        assertNull(result)
    }

    @Test
    fun readJsonlReturnsNullWhenFileIsEmpty() {
        val dir = TestFixtures.tempDir("empty-jsonl")
        val file = File(dir, "empty.jsonl").apply {
            writeText("")
        }

        val result = readJsonl(file.absolutePath)

        assertNull(result)
    }

    @Test
    fun readJsonlReturnsNullWhenJsonIsMalformed() {
        val dir = TestFixtures.tempDir("malformed-jsonl")
        val file = File(dir, "broken.jsonl").apply {
            writeText("{not-valid-json")
        }

        val result = readJsonl(file.absolutePath)

        assertNull(result)
    }

    @Test
    fun readJsonlReturnsParsedObjectWhenJsonIsValid() {
        val dir = TestFixtures.tempDir("valid-jsonl")
        val file = File(dir, "valid.jsonl").apply {
            writeText("""{"original":"a","modified":"b","checked":true}""")
        }

        val result = readJsonl(file.absolutePath)

        assertNotNull(result)
        assertEquals("a", result?.getString("original"))
        assertEquals("b", result?.getString("modified"))
        assertEquals(true, result?.getBoolean("checked"))
    }
}
