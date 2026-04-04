package tw.com.johnnyhng.eztalk.asr.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.managers.SettingsManager
import java.io.File

class FileManagerBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fileManagerShowsSeededEntryMetadata() {
        val userId = "file-manager-metadata"
        clearUserFiles(userId)
        seedSettings(userId)
        seedEntry(
            userId = userId,
            filename = "sample",
            originalText = "original line",
            modifiedText = "modified line"
        )

        val viewModel = HomeViewModel(application)
        composeRule.setContent {
            FileManagerScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText("sample.wav").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.original_text, "original line") +
                "\n" +
                context.getString(R.string.modified_text, "modified line")
        )
            .assertIsDisplayed()
    }

    @Test
    fun selectAllChecksOnlyMutableEntries() {
        val userId = "file-manager-select-all"
        clearUserFiles(userId)
        seedSettings(userId)
        seedEntry(
            userId = userId,
            filename = "mutable",
            originalText = "mutable original",
            modifiedText = "mutable modified",
            mutable = true,
            checked = false
        )
        seedEntry(
            userId = userId,
            filename = "immutable",
            originalText = "immutable original",
            modifiedText = "immutable modified",
            mutable = false,
            checked = false
        )
        val mutableJson = File(application.filesDir, "wavs/$userId/mutable.jsonl")
        val immutableJson = File(application.filesDir, "wavs/$userId/immutable.jsonl")

        val viewModel = HomeViewModel(application)
        composeRule.setContent {
            FileManagerScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText(context.getString(R.string.select_all))
            .assertIsDisplayed()
        composeRule.onNode(
            isToggleable() and hasAnySibling(hasText(context.getString(R.string.select_all)))
        ).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            JSONObject(mutableJson.readText()).getBoolean("checked") &&
                !JSONObject(immutableJson.readText()).getBoolean("checked")
        }
    }

    @Test
    fun selectAllSecondClickUnchecksMutableEntriesAgain() {
        val userId = "file-manager-select-all-toggle"
        clearUserFiles(userId)
        seedSettings(userId)
        seedEntry(
            userId = userId,
            filename = "mutable",
            originalText = "mutable original",
            modifiedText = "mutable modified",
            mutable = true,
            checked = false
        )
        val mutableJson = File(application.filesDir, "wavs/$userId/mutable.jsonl")

        val viewModel = HomeViewModel(application)
        composeRule.setContent {
            FileManagerScreen(homeViewModel = viewModel)
        }

        val selectAll = composeRule.onNode(
            isToggleable() and hasAnySibling(hasText(context.getString(R.string.select_all)))
        )

        selectAll.performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            JSONObject(mutableJson.readText()).getBoolean("checked")
        }

        selectAll.performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !JSONObject(mutableJson.readText()).getBoolean("checked")
        }
    }

    @Test
    fun immutableEntryCheckboxIsDisabled() {
        val userId = "file-manager-immutable-disabled"
        clearUserFiles(userId)
        seedSettings(userId)
        seedEntry(
            userId = userId,
            filename = "immutable",
            originalText = "immutable original",
            modifiedText = "immutable modified",
            mutable = false,
            checked = false
        )

        val viewModel = HomeViewModel(application)
        composeRule.setContent {
            FileManagerScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText("immutable.wav").assertIsDisplayed()
        composeRule.onNode(
            isToggleable() and hasAnySibling(hasText("immutable.wav"))
        ).assertIsNotEnabled()
    }

    @Test
    fun deleteIconRemovesSingleEntryFiles() {
        val userId = "file-manager-delete"
        clearUserFiles(userId)
        seedSettings(userId)
        val checkedWav = seedEntry(
            userId = userId,
            filename = "checked",
            originalText = "checked original",
            modifiedText = "checked modified",
            checked = true
        )
        val uncheckedWav = seedEntry(
            userId = userId,
            filename = "unchecked",
            originalText = "unchecked original",
            modifiedText = "unchecked modified",
            checked = false
        )

        val checkedJson = File(checkedWav.parentFile, "checked.jsonl")
        val uncheckedJson = File(uncheckedWav.parentFile, "unchecked.jsonl")

        val viewModel = HomeViewModel(application)
        composeRule.setContent {
            FileManagerScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText("checked.wav")
            .assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription(context.getString(R.string.delete)) and
                hasAnySibling(hasText("checked.wav"))
        )
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !checkedWav.exists() && !checkedJson.exists() && uncheckedWav.exists() && uncheckedJson.exists()
        }

        assertFalse(checkedWav.exists())
        assertFalse(checkedJson.exists())
        assertTrue(uncheckedWav.exists())
        assertTrue(uncheckedJson.exists())
    }

    private fun seedSettings(userId: String) = runBlocking {
        SettingsManager(application).updateSettings(
            UserSettings(
                userId = userId,
                backendUrl = "https://example.com",
                inlineEdit = true,
                enableTtsFeedback = false
            )
        )
    }

    private fun clearUserFiles(userId: String) {
        File(application.filesDir, "wavs/$userId").deleteRecursively()
    }

    private fun seedEntry(
        userId: String,
        filename: String,
        originalText: String,
        modifiedText: String,
        checked: Boolean = false,
        mutable: Boolean = true,
        removable: Boolean = false,
        localCandidates: List<String> = emptyList(),
        remoteCandidates: List<String> = emptyList()
    ): File {
        val wavsDir = File(application.filesDir, "wavs/$userId").apply { mkdirs() }
        val wavFile = File(wavsDir, "$filename.wav")
        wavFile.writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46))

        val jsonFile = File(wavsDir, "$filename.jsonl")
        jsonFile.writeText(
            JSONObject()
                .put("original", originalText)
                .put("modified", modifiedText)
                .put("checked", checked)
                .put("mutable", mutable)
                .put("removable", removable)
                .put("local_candidates", JSONArray(localCandidates))
                .put("remote_candidates", JSONArray(remoteCandidates))
                .toString()
        )
        return wavFile
    }
}
