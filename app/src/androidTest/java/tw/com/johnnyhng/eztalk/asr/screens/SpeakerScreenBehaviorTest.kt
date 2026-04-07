package tw.com.johnnyhng.eztalk.asr.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.managers.SettingsManager
import java.io.File

class SpeakerScreenBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun speakerScreenStartsCollapsedWithoutSelectedDocument() {
        val userId = "speaker-initial-state"
        clearSpeakerFiles(userId)
        seedSettings(userId)
        seedSpeakerDocument(userId, "alpha", "first.txt", "alpha body")
        seedSpeakerDocument(userId, "beta", "second.txt", "beta body")

        val viewModel = HomeViewModel(application)
        composeRule.setContent {
            SpeakerScreen(homeViewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("alpha").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("alpha").assertIsDisplayed()
        composeRule.onNodeWithText("beta").assertIsDisplayed()
        composeRule.onAllNodesWithText("first.txt").assertCountEquals(0)
        composeRule.onAllNodesWithText("second.txt").assertCountEquals(0)
        composeRule.onNodeWithText(
            context.getString(R.string.speaker_no_document_selected)
        ).assertIsDisplayed()
    }

    @Test
    fun speakerScreenOnlyKeepsOneFolderExpandedAtATime() {
        val userId = "speaker-accordion"
        clearSpeakerFiles(userId)
        seedSettings(userId)
        seedSpeakerDocument(userId, "alpha", "first.txt", "alpha body")
        seedSpeakerDocument(userId, "beta", "second.txt", "beta body")

        val viewModel = HomeViewModel(application)
        composeRule.setContent {
            SpeakerScreen(homeViewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("alpha").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("alpha").performClick()
        composeRule.onNodeWithText("first.txt").assertIsDisplayed()

        composeRule.onNodeWithText("beta").performClick()
        composeRule.onNodeWithText("second.txt").assertIsDisplayed()
        composeRule.onAllNodesWithText("first.txt").assertCountEquals(0)
    }

    @Test
    fun speakerScreenCancelEditRestoresOriginalText() {
        val userId = "speaker-cancel-edit"
        clearSpeakerFiles(userId)
        seedSettings(userId)
        val textFile = seedSpeakerDocument(userId, "alpha", "draft.txt", "original body")

        val viewModel = HomeViewModel(application)
        composeRule.setContent {
            SpeakerScreen(homeViewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("alpha").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("alpha").performClick()
        composeRule.onNodeWithText("draft.txt").performClick()
        composeRule.onNodeWithText("original body").assertIsDisplayed()

        composeRule.onNodeWithContentDescription(context.getString(R.string.edit)).performClick()

        val editor = composeRule.onNode(hasSetTextAction())
        editor.performTextClearance()
        editor.performTextInput("edited body")

        composeRule.onNodeWithText(context.getString(R.string.cancel_edit)).performClick()

        composeRule.onNodeWithText("original body").assertIsDisplayed()
        composeRule.onAllNodesWithText("edited body").assertCountEquals(0)
        assertEquals("original body", textFile.readText())
    }

    @Test
    fun speakerScreenSaveEditWritesUpdatedTextToFile() {
        val userId = "speaker-save-edit"
        clearSpeakerFiles(userId)
        seedSettings(userId)
        val textFile = seedSpeakerDocument(userId, "alpha", "draft.txt", "original body")

        val viewModel = HomeViewModel(application)
        composeRule.setContent {
            SpeakerScreen(homeViewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("alpha").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("alpha").performClick()
        composeRule.onNodeWithText("draft.txt").performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.edit)).performClick()

        val editor = composeRule.onNode(hasSetTextAction())
        editor.performTextClearance()
        editor.performTextInput("saved body")

        composeRule.onNodeWithContentDescription(context.getString(R.string.confirm_edit))
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            textFile.readText() == "saved body"
        }

        composeRule.onNodeWithText("saved body").assertIsDisplayed()
    }

    @Test
    fun speakerScreenSelectingFileAutoOpensContentPane() {
        val userId = "speaker-auto-open-content"
        clearSpeakerFiles(userId)
        seedSettings(userId)
        seedSpeakerDocument(userId, "alpha", "draft.txt", "original body")

        val viewModel = HomeViewModel(application)
        composeRule.setContent {
            SpeakerScreen(homeViewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("alpha").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodesWithText(context.getString(R.string.speaker_content_title)).assertCountEquals(0)
        composeRule.onNodeWithText("alpha").performClick()
        composeRule.onNodeWithText("draft.txt").performClick()

        composeRule.onNodeWithText(context.getString(R.string.speaker_content_title)).assertIsDisplayed()
        composeRule.onNodeWithText("original body").assertIsDisplayed()
    }

    @Test
    fun speakerScreenCollapsingSelectedFolderClearsAndHidesContent() {
        val userId = "speaker-collapse-clears-content"
        clearSpeakerFiles(userId)
        seedSettings(userId)
        seedSpeakerDocument(userId, "alpha", "draft.txt", "original body")

        val viewModel = HomeViewModel(application)
        composeRule.setContent {
            SpeakerScreen(homeViewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("alpha").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("alpha").performClick()
        composeRule.onNodeWithText("draft.txt").performClick()
        composeRule.onNodeWithText("original body").assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.speaker_explorer_pane_title)).performClick()
        composeRule.onNodeWithText("alpha").performClick()

        composeRule.onAllNodesWithText(context.getString(R.string.speaker_content_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText("original body").assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.speaker_no_document_selected)).assertCountEquals(0)
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

    private fun clearSpeakerFiles(userId: String) {
        File(application.filesDir, "speech/$userId").deleteRecursively()
    }

    private fun seedSpeakerDocument(
        userId: String,
        folderName: String,
        fileName: String,
        content: String
    ): File {
        val folder = File(application.filesDir, "speech/$userId/$folderName").apply { mkdirs() }
        return File(folder, fileName).apply { writeText(content) }
    }
}
