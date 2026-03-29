package tw.com.johnnyhng.eztalk.asr.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tw.com.johnnyhng.eztalk.asr.data.classes.QueueState
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UtilsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun sha256StringReturnsExpectedDigest() {
        val digest = sha256("abc")

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            digest
        )
    }

    @Test
    fun sha256FileReturnsExpectedDigestAndNullForMissingFile() {
        val file = File(context.filesDir, "hash.txt").apply {
            writeText("abc")
        }

        val digest = sha256(file)
        val missingDigest = sha256(File(context.filesDir, "missing-hash.txt"))

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            digest
        )
        assertNull(missingDigest)
    }

    @Test
    fun saveQueueStateAndRestoreQueueStateRoundTrip() {
        val userId = "utils-queue-roundtrip"
        val state = QueueState(
            currentText = "current line",
            queue = mutableListOf("next-1", "next-2")
        )

        saveQueueState(context, userId, state)
        val restored = restoreQueueState(context, userId)

        assertNotNull(restored)
        assertEquals("current line", restored?.currentText)
        assertEquals(listOf("next-1", "next-2"), restored?.queue)
    }

    @Test
    fun restoreQueueStateReturnsNullWhenStateFileIsMissingOrMalformed() {
        val missing = restoreQueueState(context, "utils-missing")

        val dir = File(context.filesDir, "data/utils-malformed").apply {
            mkdirs()
        }
        File(dir, "seq.jsonl").writeText("{bad")
        val malformed = restoreQueueState(context, "utils-malformed")

        assertNull(missing)
        assertNull(malformed)
    }

    @Test
    fun deleteQueueStateRemovesSavedStateFileAndIgnoresMissingFile() {
        val userId = "utils-delete"
        val state = QueueState(
            currentText = "current line",
            queue = mutableListOf("next")
        )
        saveQueueState(context, userId, state)

        val file = File(context.filesDir, "data/$userId/seq.jsonl")
        assertTrue(file.exists())

        deleteQueueState(context, userId)
        assertFalse(file.exists())

        deleteQueueState(context, userId)
        assertFalse(file.exists())
    }
}
