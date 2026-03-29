package tw.com.johnnyhng.eztalk.asr.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isToggleable
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.R

class DataCollectWidgetBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun manualModeShowsEditableTextFieldAndClearAction() {
        var latestText = ""
        var clearClicks = 0

        composeRule.setContent {
            DataCollectWidget(
                text = "",
                onTextChange = { latestText = it },
                onTtsClick = {},
                isSequenceMode = false,
                onSequenceModeChange = {},
                onUploadClick = {},
                onPreviousClick = {},
                onNextClick = {},
                onDeleteClick = { clearClicks += 1 },
                isPreviousEnabled = false,
                isNextEnabled = false,
                isSequenceModeSwitchEnabled = true,
                showNoQueueMessage = false
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.text_for_recording))
            .assertIsDisplayed()
            .performTextInput("hello")
        composeRule.onNodeWithContentDescription(context.getString(R.string.clear_text))
            .assertIsDisplayed()
            .performClick()

        assertEquals("hello", latestText)
        assertEquals(1, clearClicks)
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.previous)).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.next)).assertCountEquals(0)
    }

    @Test
    fun sequenceModeShowsQueueControlsAndHidesEditableField() {
        var previousClicks = 0
        var nextClicks = 0
        var uploadClicks = 0
        var ttsClicks = 0

        composeRule.setContent {
            DataCollectWidget(
                text = "queued line",
                onTextChange = {},
                onTtsClick = { ttsClicks += 1 },
                isSequenceMode = true,
                onSequenceModeChange = {},
                onUploadClick = { uploadClicks += 1 },
                onPreviousClick = { previousClicks += 1 },
                onNextClick = { nextClicks += 1 },
                onDeleteClick = {},
                isPreviousEnabled = true,
                isNextEnabled = true,
                isSequenceModeSwitchEnabled = true,
                showNoQueueMessage = true
            )
        }

        composeRule.onNodeWithText("queued line").assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.text_for_recording)).assertCountEquals(0)
        composeRule.onNodeWithText(context.getString(R.string.please_upload_txt)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.upload_txt)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.tts_for_data_collection)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.previous)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.next)).performClick()

        assertEquals(1, uploadClicks)
        assertEquals(1, ttsClicks)
        assertEquals(1, previousClicks)
        assertEquals(1, nextClicks)
    }

    @Test
    fun sequenceModeSwitchRespectsEnabledStateAndCallback() {
        var toggledTo: Boolean? = null

        composeRule.setContent {
            DataCollectWidget(
                text = "",
                onTextChange = {},
                onTtsClick = {},
                isSequenceMode = false,
                onSequenceModeChange = { toggledTo = it },
                onUploadClick = {},
                onPreviousClick = {},
                onNextClick = {},
                onDeleteClick = {},
                isPreviousEnabled = false,
                isNextEnabled = false,
                isSequenceModeSwitchEnabled = true,
                showNoQueueMessage = false
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.sequence_mode))
            .assertIsDisplayed()
        composeRule.onNode(
            isToggleable() and hasAnySibling(
                hasText(context.getString(R.string.sequence_mode))
            )
        ).performClick()

        assertEquals(true, toggledTo)
    }
}
