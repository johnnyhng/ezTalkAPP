package tw.com.johnnyhng.eztalk.asr.managers

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings

class SettingsManagerTest {

    @Test
    fun preferencesToUserSettingsUsesDefaultsWhenPreferencesAreEmpty() {
        val settings = preferencesToUserSettings(mutablePreferencesOf())

        assertEquals("default_user", settings.userId)
        assertEquals(1000f, settings.lingerMs)
        assertEquals(500f, settings.partialIntervalMs)
        assertFalse(settings.saveVadSegmentsOnly)
        assertTrue(settings.inlineEdit)
        assertEquals("https://120.126.151.159:56432", settings.backendUrl)
        assertEquals("", settings.recognitionUrl)
        assertFalse(settings.enableTtsFeedback)
        assertEquals("", settings.modelUrl)
        assertEquals("", settings.selectedModelName)
    }

    @Test
    fun preferencesToUserSettingsMapsStoredValues() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("user_id") to "tester",
            floatPreferencesKey("linger_ms") to 1234f,
            floatPreferencesKey("partial_interval_ms") to 678f,
            booleanPreferencesKey("save_vad_segments_only") to true,
            booleanPreferencesKey("inline_edit") to false,
            stringPreferencesKey("backend_url") to "https://example.com",
            stringPreferencesKey("recognition_url") to "https://example.com/process_audio",
            booleanPreferencesKey("enable_tts_feedback") to true,
            stringPreferencesKey("model_url") to "https://models.example.com/default.zip",
            stringPreferencesKey("selected_model_name") to "demo-model"
        )

        val settings = preferencesToUserSettings(preferences)

        assertEquals("tester", settings.userId)
        assertEquals(1234f, settings.lingerMs)
        assertEquals(678f, settings.partialIntervalMs)
        assertTrue(settings.saveVadSegmentsOnly)
        assertFalse(settings.inlineEdit)
        assertEquals("https://example.com", settings.backendUrl)
        assertEquals("https://example.com/process_audio", settings.recognitionUrl)
        assertTrue(settings.enableTtsFeedback)
        assertEquals("https://models.example.com/default.zip", settings.modelUrl)
        assertEquals("demo-model", settings.selectedModelName)
    }

    @Test
    fun writeUserSettingsPopulatesAllPreferenceKeys() {
        val preferences = mutablePreferencesOf()
        val settings = UserSettings(
            userId = "writer",
            lingerMs = 1111f,
            partialIntervalMs = 222f,
            saveVadSegmentsOnly = true,
            inlineEdit = false,
            backendUrl = "https://backend.example.com",
            recognitionUrl = "https://backend.example.com/process_audio",
            enableTtsFeedback = true,
            modelUrl = "https://models.example.com/model.zip",
            selectedModelName = "model-a"
        )

        writeUserSettings(preferences, settings)

        assertEquals("writer", preferences[stringPreferencesKey("user_id")])
        assertEquals(1111f, preferences[floatPreferencesKey("linger_ms")])
        assertEquals(222f, preferences[floatPreferencesKey("partial_interval_ms")])
        assertEquals(true, preferences[booleanPreferencesKey("save_vad_segments_only")])
        assertEquals(false, preferences[booleanPreferencesKey("inline_edit")])
        assertEquals("https://backend.example.com", preferences[stringPreferencesKey("backend_url")])
        assertEquals("https://backend.example.com/process_audio", preferences[stringPreferencesKey("recognition_url")])
        assertEquals(true, preferences[booleanPreferencesKey("enable_tts_feedback")])
        assertEquals("https://models.example.com/model.zip", preferences[stringPreferencesKey("model_url")])
        assertEquals("model-a", preferences[stringPreferencesKey("selected_model_name")])
    }
}
