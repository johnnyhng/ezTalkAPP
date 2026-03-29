package tw.com.johnnyhng.eztalk.asr.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.widgets.CandidateList

class HomeCandidateListBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun candidateListShowsRowTextAndActionButtonsForPlayableTranscript() {
        composeRule.setContent {
            CandidateList(
                resultList = listOf(
                    transcript(
                        recognizedText = "recognized line",
                        modifiedText = "edited line",
                        wavFilePath = "/tmp/sample.wav"
                    )
                ),
                lazyListState = rememberLazyListState(),
                isInteractionLocked = false,
                isInlineEditing = false,
                editingIndex = -1,
                editingText = "",
                onEditingTextChange = {},
                onCancelEdit = {},
                onConfirmEdit = { _, _ -> },
                onItemClick = { _, _ -> },
                onTtsClick = { _, _ -> },
                onPlayClick = {},
                onDeleteClick = { _, _ -> },
                isRecognizingSpeech = false,
                currentlyPlaying = null,
                isStarted = false,
                isTtsSpeaking = false,
                countdownProgress = 0f,
                isDataCollectMode = false,
                inlineEditEnabled = true,
                localCandidate = null,
                isFetchingCandidates = false
            )
        }

        composeRule.onNodeWithText("1: edited line").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription(context.getString(R.string.talk)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.play)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.delete)).assertIsDisplayed()
    }

    @Test
    fun candidateListInvokesCallbacksFromRowAndActionButtons() {
        var clickedIndex = -1
        var clickedText = ""
        var playedPath = ""
        var deletedIndex = -1
        var deletedPath = ""

        val transcript = transcript(
            recognizedText = "recognized line",
            modifiedText = "edited line",
            wavFilePath = "/tmp/callback.wav"
        )

        composeRule.setContent {
            CandidateList(
                resultList = listOf(transcript),
                lazyListState = rememberLazyListState(),
                isInteractionLocked = false,
                isInlineEditing = false,
                editingIndex = -1,
                editingText = "",
                onEditingTextChange = {},
                onCancelEdit = {},
                onConfirmEdit = { _, _ -> },
                onItemClick = { index, item ->
                    clickedIndex = index
                    clickedText = item.modifiedText
                },
                onTtsClick = { _, text -> clickedText = text },
                onPlayClick = { playedPath = it },
                onDeleteClick = { index, path ->
                    deletedIndex = index
                    deletedPath = path
                },
                isRecognizingSpeech = false,
                currentlyPlaying = null,
                isStarted = false,
                isTtsSpeaking = false,
                countdownProgress = 0f,
                isDataCollectMode = false,
                inlineEditEnabled = true,
                localCandidate = null,
                isFetchingCandidates = false
            )
        }

        composeRule.onNodeWithText("1: edited line").performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.play)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.delete)).performClick()

        assertEquals(0, clickedIndex)
        assertEquals("edited line", clickedText)
        assertEquals("/tmp/callback.wav", playedPath)
        assertEquals(0, deletedIndex)
        assertEquals("/tmp/callback.wav", deletedPath)
    }

    @Test
    fun candidateListShowsInlineEditRowAndLocksPlaybackActionsWhileEditing() {
        composeRule.setContent {
            val editingText = remember { mutableStateOf("edited line") }
            CandidateList(
                resultList = listOf(
                    transcript(
                        recognizedText = "recognized line",
                        modifiedText = "edited line",
                        wavFilePath = "/tmp/editing.wav",
                        localCandidates = listOf("local option"),
                        remoteCandidates = listOf("remote option")
                    )
                ),
                lazyListState = rememberLazyListState(),
                isInteractionLocked = true,
                isInlineEditing = true,
                editingIndex = 0,
                editingText = editingText.value,
                onEditingTextChange = { editingText.value = it },
                onCancelEdit = {},
                onConfirmEdit = { _, _ -> },
                onItemClick = { _, _ -> },
                onTtsClick = { _, _ -> },
                onPlayClick = {},
                onDeleteClick = { _, _ -> },
                isRecognizingSpeech = false,
                currentlyPlaying = null,
                isStarted = false,
                isTtsSpeaking = false,
                countdownProgress = 0f,
                isDataCollectMode = false,
                inlineEditEnabled = true,
                localCandidate = "inline local",
                isFetchingCandidates = false
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.edit)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.cancel_edit)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.confirm_edit)).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.play)).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.delete)).assertCountEquals(0)
    }

    @Test
    fun candidateItemRowDisablesActionsWhileScreenIsLocked() {
        composeRule.setContent {
            CandidateList(
                resultList = listOf(
                    transcript(
                        recognizedText = "recognized line",
                        modifiedText = "edited line",
                        wavFilePath = "/tmp/locked.wav"
                    )
                ),
                lazyListState = rememberLazyListState(),
                isInteractionLocked = true,
                isInlineEditing = false,
                editingIndex = -1,
                editingText = "",
                onEditingTextChange = {},
                onCancelEdit = {},
                onConfirmEdit = { _, _ -> },
                onItemClick = { _, _ -> },
                onTtsClick = { _, _ -> },
                onPlayClick = {},
                onDeleteClick = { _, _ -> },
                isRecognizingSpeech = false,
                currentlyPlaying = null,
                isStarted = false,
                isTtsSpeaking = false,
                countdownProgress = 0f,
                isDataCollectMode = false,
                inlineEditEnabled = true,
                localCandidate = null,
                isFetchingCandidates = false
            )
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.talk)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(context.getString(R.string.play)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(context.getString(R.string.delete)).assertIsNotEnabled()
    }

    private fun transcript(
        recognizedText: String,
        modifiedText: String,
        wavFilePath: String,
        localCandidates: List<String> = emptyList(),
        remoteCandidates: List<String> = emptyList()
    ) = Transcript(
        recognizedText = recognizedText,
        modifiedText = modifiedText,
        wavFilePath = wavFilePath,
        localCandidates = localCandidates,
        remoteCandidates = remoteCandidates
    )
}
