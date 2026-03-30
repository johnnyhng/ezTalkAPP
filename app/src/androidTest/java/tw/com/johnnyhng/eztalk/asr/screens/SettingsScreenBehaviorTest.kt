package tw.com.johnnyhng.eztalk.asr.screens

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.managers.SettingsManager

class SettingsScreenBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

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

        composeRule.onNodeWithText(context.getString(R.string.backend_url))
            .performScrollTo()
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

        composeRule.onNodeWithText(context.getString(R.string.inline_edit)).performScrollTo()
        composeRule.onNodeWithText(context.getString(R.string.enable_tts_feedback)).performScrollTo()
        composeRule.onAllNodes(isToggleable()).assertCountEquals(3)

        val inlineEditSwitch = composeRule.onAllNodes(isToggleable())[1]
        val ttsFeedbackSwitch = composeRule.onAllNodes(isToggleable())[2]

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

        composeRule.onNodeWithText(context.getString(R.string.asr_model)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.backend_url))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.inline_edit))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.enable_tts_feedback))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreenSaveModeSwitchReflectsPersistedStateAndToggles() {
        seedSettings(
            UserSettings(
                userId = "screen-user-save-mode",
                saveVadSegmentsOnly = false
            )
        )
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            SettingsScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText(context.getString(R.string.save_vad_segments)).performScrollTo()
        composeRule.onNodeWithText(context.getString(R.string.save_full_audio)).performScrollTo()
        composeRule.onAllNodes(isToggleable()).assertCountEquals(3)

        val saveModeSwitch = composeRule.onAllNodes(isToggleable())[0]
        saveModeSwitch.assertIsOn()
        saveModeSwitch.performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            try {
                saveModeSwitch.assertIsOff()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test
    fun settingsScreenEditedBackendAndModelUrlStayVisibleAfterEdit() {
        seedSettings(
            UserSettings(
                userId = "screen-user-urls",
                backendUrl = "https://old-backend.example.com",
                modelUrl = "https://old-model.example.com/model.zip"
            )
        )
        val viewModel = HomeViewModel(application)

        composeRule.setContent {
            SettingsScreen(homeViewModel = viewModel)
        }

        composeRule.onNodeWithText(context.getString(R.string.model_download_url))
            .performScrollTo()
        composeRule.onNodeWithText("https://old-model.example.com/model.zip")
            .performTextReplacement("https://new-model.example.com/model.zip")

        composeRule.onNodeWithText(context.getString(R.string.backend_url))
            .performScrollTo()
        composeRule.onNodeWithText("https://old-backend.example.com")
            .performTextReplacement("https://new-backend.example.com")

        composeRule.onNodeWithText("https://new-model.example.com/model.zip").assertIsDisplayed()
        composeRule.onNodeWithText("https://new-backend.example.com")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun seedSettings(settings: UserSettings) = runBlocking {
        SettingsManager(application).updateSettings(settings)
    }
}
