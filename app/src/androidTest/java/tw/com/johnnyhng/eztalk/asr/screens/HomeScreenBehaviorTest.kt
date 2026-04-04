package tw.com.johnnyhng.eztalk.asr.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.managers.SettingsManager

class HomeScreenBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun homeScreenShowsTopControlsOnInitialLoad() {
        seedSettings("home-screen-initial")
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            HomeScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText(context.getString(R.string.start)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.copy)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.clear)).assertIsDisplayed()
    }

    @Test
    fun homeScreenClearButtonDoesNotCrashWhenTranscriptListIsEmpty() {
        seedSettings("home-screen-clear")
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            HomeScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText(context.getString(R.string.clear))
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithText(context.getString(R.string.start)).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.talk)).assertCountEquals(0)
    }

    @Test
    fun homeScreenClearRemovesVisibleTranscriptRows() {
        seedSettings("home-screen-clear-visible")
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            HomeScreen(homeViewModel = viewModel)
        }

        emitFinalTranscript(
            viewModel = viewModel,
            transcript = Transcript(
                recognizedText = "recognized line",
                modifiedText = "visible line",
                wavFilePath = "/tmp/home-visible.wav"
            )
        )
        composeRule.onNodeWithText("1: visible line").assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.clear)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.start)).assertIsDisplayed()
        composeRule.onNodeWithText("1: visible line").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.talk)).assertCountEquals(0)
    }

    @Test
    fun homeScreenCopyIsSafeAfterVisibleTranscriptAppears() {
        seedSettings("home-screen-copy-visible")
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            HomeScreen(homeViewModel = viewModel)
        }

        emitFinalTranscript(
            viewModel = viewModel,
            transcript = Transcript(
                recognizedText = "recognized line",
                modifiedText = "copyable line",
                wavFilePath = "/tmp/home-copy.wav"
            )
        )
        composeRule.onNodeWithText("1: copyable line").assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.copy)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.start)).assertIsDisplayed()
        composeRule.onNodeWithText("1: copyable line").assertIsDisplayed()
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

    private fun emitFinalTranscript(viewModel: HomeViewModel, transcript: Transcript) = runBlocking {
        val field = HomeViewModel::class.java.getDeclaredField("_finalTranscript")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableSharedFlow<Transcript>
        flow.emit(transcript)
    }
}
