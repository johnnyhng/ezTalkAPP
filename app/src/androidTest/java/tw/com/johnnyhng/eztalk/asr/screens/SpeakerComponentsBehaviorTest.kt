package tw.com.johnnyhng.eztalk.asr.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.R

class SpeakerComponentsBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun speakerContentScreenClickingLineCallsSpeakLineWithIndexAndText() {
        var clickedIndex = -1
        var clickedText = ""

        composeRule.setContent {
            SpeakerContentScreen(
                selectedDocument = SpeakerDocumentUi(
                    id = "/tmp/demo.txt",
                    displayName = "demo.txt",
                    previewText = "",
                    fullText = "first line\nsecond line"
                ),
                isTtsReady = true,
                isPlaying = false,
                isPaused = false,
                isEditing = false,
                currentPlayingLineIndex = null,
                editingText = "",
                onEditingTextChange = {},
                onSpeakLine = { index, line ->
                    clickedIndex = index
                    clickedText = line
                },
                onPlay = {},
                onPause = {},
                onStop = {},
                onEdit = {},
                onSave = {},
                onCancelEdit = {}
            )
        }

        composeRule.onNodeWithText("first line").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("second line").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(1, clickedIndex)
            assertEquals("second line", clickedText)
        }
    }

    @Test
    fun speakerContentScreenDisablesEditWhilePlaying() {
        composeRule.setContent {
            SpeakerContentScreen(
                selectedDocument = SpeakerDocumentUi(
                    id = "/tmp/demo.txt",
                    displayName = "demo.txt",
                    previewText = "",
                    fullText = "first line"
                ),
                isTtsReady = true,
                isPlaying = true,
                isPaused = false,
                isEditing = false,
                currentPlayingLineIndex = 0,
                editingText = "",
                onEditingTextChange = {},
                onSpeakLine = { _, _ -> },
                onPlay = {},
                onPause = {},
                onStop = {},
                onEdit = {},
                onSave = {},
                onCancelEdit = {}
            )
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.edit))
            .assertIsNotEnabled()
    }

    @Test
    fun speakerContentScreenDisablesEditWhilePaused() {
        composeRule.setContent {
            SpeakerContentScreen(
                selectedDocument = SpeakerDocumentUi(
                    id = "/tmp/demo.txt",
                    displayName = "demo.txt",
                    previewText = "",
                    fullText = "first line"
                ),
                isTtsReady = true,
                isPlaying = false,
                isPaused = true,
                isEditing = false,
                currentPlayingLineIndex = 0,
                editingText = "",
                onEditingTextChange = {},
                onSpeakLine = { _, _ -> },
                onPlay = {},
                onPause = {},
                onStop = {},
                onEdit = {},
                onSave = {},
                onCancelEdit = {}
            )
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.edit))
            .assertIsNotEnabled()
    }

    @Test
    fun speechFileExplorerDisablesSingleFileDeleteWhenFlagIsFalse() {
        composeRule.setContent {
            SpeechFileExplorer(
                directories = listOf(
                    SpeakerDirectoryUi(
                        id = "/tmp/alpha",
                        displayName = "alpha",
                        isExpanded = true,
                        documents = listOf(
                            SpeakerDocumentUi(
                                id = "/tmp/alpha/demo.txt",
                                displayName = "demo.txt",
                                previewText = "",
                                fullText = "body"
                            )
                        )
                    )
                ),
                selectedDocumentId = null,
                isLoading = false,
                isImportEnabled = true,
                isDirectoryDeleteEnabled = true,
                isDocumentDeleteEnabled = false,
                onCreateFolder = {},
                onGoogleDriveImport = {},
                onToggleExpand = {},
                onRefresh = {},
                onImportIntoDirectory = {},
                onRemoveDirectory = {},
                onRemoveDocument = {},
                onDocumentSelected = {}
            )
        }

        composeRule.onNode(
            hasContentDescription(context.getString(R.string.delete)) and
                hasAnySibling(hasText("demo.txt"))
        ).assertIsNotEnabled()
    }

    @Test
    fun speechFileExplorerDisablesFolderDeleteWhenFlagIsFalse() {
        composeRule.setContent {
            SpeechFileExplorer(
                directories = listOf(
                    SpeakerDirectoryUi(
                        id = "/tmp/alpha",
                        displayName = "alpha",
                        isExpanded = false,
                        documents = emptyList()
                    )
                ),
                selectedDocumentId = null,
                isLoading = false,
                isImportEnabled = true,
                isDirectoryDeleteEnabled = false,
                isDocumentDeleteEnabled = true,
                onCreateFolder = {},
                onGoogleDriveImport = {},
                onToggleExpand = {},
                onRefresh = {},
                onImportIntoDirectory = {},
                onRemoveDirectory = {},
                onRemoveDocument = {},
                onDocumentSelected = {}
            )
        }

        composeRule.onNode(
            hasContentDescription(context.getString(R.string.speaker_remove_folder)) and
                hasAnySibling(hasText("alpha"))
        ).assertIsNotEnabled()
    }
}
