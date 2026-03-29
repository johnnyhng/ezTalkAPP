package tw.com.johnnyhng.eztalk.asr.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WavUtilSaveJsonlCandidatesTest {
    @Test
    fun saveJsonlWritesLocalAndRemoteCandidatesWhenProvided() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val userId = "batch5_user"
        val filename = "candidate-fields"

        saveJsonl(
            context = context,
            userId = userId,
            filename = filename,
            originalText = "original",
            modifiedText = "modified",
            checked = false,
            localCandidates = listOf("local-1", "local-2"),
            remoteCandidates = listOf("remote-1", "remote-2")
        )

        val file = File(context.filesDir, "wavs/$userId/$filename.jsonl")
        val json = JSONObject(file.readText())

        assertEquals(
            listOf("local-1", "local-2"),
            json.optStringList("local_candidates")
        )
        assertEquals(
            listOf("remote-1", "remote-2"),
            json.optStringList("remote_candidates")
        )
    }

    @Test
    fun saveJsonlOmitsCandidateFieldsWhenNotProvided() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val userId = "batch5_user_no_candidates"
        val filename = "no-candidates"

        saveJsonl(
            context = context,
            userId = userId,
            filename = filename,
            originalText = "original",
            modifiedText = "modified",
            checked = true
        )

        val file = File(context.filesDir, "wavs/$userId/$filename.jsonl")
        val json = JSONObject(file.readText())

        assertFalse(json.has("local_candidates"))
        assertFalse(json.has("remote_candidates"))
    }

    @Test
    fun saveJsonlWritesEmptyArraysWhenExplicitEmptyCandidateListsAreProvided() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val userId = "batch5_user_empty_candidates"
        val filename = "empty-candidates"

        saveJsonl(
            context = context,
            userId = userId,
            filename = filename,
            originalText = "original",
            modifiedText = "modified",
            checked = false,
            localCandidates = emptyList(),
            remoteCandidates = emptyList()
        )

        val file = File(context.filesDir, "wavs/$userId/$filename.jsonl")
        val json = JSONObject(file.readText())

        assertTrue(json.has("local_candidates"))
        assertTrue(json.has("remote_candidates"))
        assertTrue(json.optStringList("local_candidates").isEmpty())
        assertTrue(json.optStringList("remote_candidates").isEmpty())
    }
}
