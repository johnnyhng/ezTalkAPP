package tw.com.johnnyhng.eztalk.asr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import tw.com.johnnyhng.eztalk.asr.managers.SettingsManager

@RunWith(AndroidJUnit4::class)
class SettingsManagerInstrumentedTest {
    @Test
    fun updateSettingsPersistsAndReadsBackValues() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settingsManager = SettingsManager(context)
        val updated = UserSettings(
            userId = "instrumented_user",
            lingerMs = 1234f,
            partialIntervalMs = 678f,
            saveVadSegmentsOnly = true,
            inlineEdit = false,
            backendUrl = "https://example.com",
            recognitionUrl = "",
            enableTtsFeedback = true,
            modelUrl = "https://models.example.com/default.zip",
            selectedModelName = "demo-model"
        )

        settingsManager.updateSettings(updated)
        val restored = settingsManager.userSettings.first()

        assertEquals(updated, restored)
        assertEquals("https://example.com/api/process_audio", restored.effectiveRecognitionUrl)
    }
}
