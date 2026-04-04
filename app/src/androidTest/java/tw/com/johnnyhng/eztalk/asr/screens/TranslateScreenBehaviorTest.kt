package tw.com.johnnyhng.eztalk.asr.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.managers.SettingsManager

class TranslateScreenBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun translateScreenShowsEditableTextFieldAndCoreControls() {
        seedSettings("translate-screen-controls")
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            TranslateScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText(context.getString(R.string.recognized_text)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.start)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.copy)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.clear)).assertIsDisplayed()
    }

    @Test
    fun translateScreenClearButtonResetsTypedText() {
        seedSettings("translate-screen-clear")
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            TranslateScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText(context.getString(R.string.recognized_text))
            .performTextInput("hello translate")
        composeRule.onNodeWithText("hello translate").assertIsDisplayed()

        composeRule.onNodeWithContentDescription(context.getString(R.string.clear)).performClick()

        composeRule.onNodeWithText("hello translate").assertDoesNotExist()
    }

    @Test
    fun translateScreenCopyButtonKeepsTypedTextAvailable() {
        seedSettings("translate-screen-copy")
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            TranslateScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText(context.getString(R.string.recognized_text))
            .performTextInput("clipboard text")
        composeRule.onNodeWithContentDescription(context.getString(R.string.copy)).performClick()
        composeRule.onNodeWithText("clipboard text").assertIsDisplayed()
    }

    @Test
    fun translateScreenCopyButtonIsSafeWhenTextIsEmpty() {
        seedSettings("translate-screen-copy-empty")
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            TranslateScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.copy)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.recognized_text)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.start)).assertIsDisplayed()
    }

    @Test
    fun translateScreenAllowsTypingAgainAfterClear() {
        seedSettings("translate-screen-retype")
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            TranslateScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText(context.getString(R.string.recognized_text))
            .performTextInput("first text")
        composeRule.onNodeWithContentDescription(context.getString(R.string.clear)).performClick()
        composeRule.onNodeWithText("first text").assertDoesNotExist()

        composeRule.onNodeWithText(context.getString(R.string.recognized_text))
            .performTextInput("second text")
        composeRule.onNodeWithText("second text").assertIsDisplayed()
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
}
