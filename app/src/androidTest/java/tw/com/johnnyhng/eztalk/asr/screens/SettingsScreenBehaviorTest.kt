package tw.com.johnnyhng.eztalk.asr.screens

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.managers.SettingsManager

class SettingsScreenBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun settingsScreenDisplaysSeededBackendUrlAndAllowsEditingIt() {
        seedSettings(
            UserSettings(
                userId = "screen-user",
                backendUrl = "https://seed.example.com",
                inlineEdit = true,
                enableTtsFeedback = false
            )
        )
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            SettingsScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText("https://seed.example.com")
            .assertIsDisplayed()
            .performTextReplacement("https://edited.example.com")

        composeRule.onNodeWithText("https://edited.example.com").assertIsDisplayed()
    }

    @Test
    fun settingsScreenSwitchesReflectAndToggleInlineEditAndTtsFeedback() {
        seedSettings(
            UserSettings(
                userId = "screen-user-switches",
                backendUrl = "https://switch.example.com",
                inlineEdit = true,
                enableTtsFeedback = false
            )
        )
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            SettingsScreen(homeViewModel = viewModel)
        }

        val inlineEditSwitch = composeRule.onNode(
            isToggleable() and hasAnySibling(hasText("Inline Edit"))
        )
        val ttsFeedbackSwitch = composeRule.onNode(
            isToggleable() and hasAnySibling(hasText("Enable TTS Feedback"))
        )

        inlineEditSwitch.assertIsOn()
        ttsFeedbackSwitch.assertIsOff()

        inlineEditSwitch.performClick()
        ttsFeedbackSwitch.performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            try {
                inlineEditSwitch.assertIsOff()
                ttsFeedbackSwitch.assertIsOn()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test
    fun settingsScreenShowsKeyControlLabels() {
        seedSettings(UserSettings(userId = "screen-user-labels"))
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            SettingsScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText("ASR Model").assertIsDisplayed()
        composeRule.onNodeWithText("backend URL").assertIsDisplayed()
        composeRule.onNodeWithText("Inline Edit").assertIsDisplayed()
        composeRule.onNodeWithText("Enable TTS Feedback").assertIsDisplayed()
    }

    private fun seedSettings(settings: UserSettings) = runBlocking {
        SettingsManager(application).updateSettings(settings)
    }
}
