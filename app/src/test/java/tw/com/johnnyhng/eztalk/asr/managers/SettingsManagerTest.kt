package tw.com.johnnyhng.eztalk.asr.managers

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
        assertEquals(5000f, settings.lingerMs)
        assertEquals(500f, settings.partialIntervalMs)
        assertFalse(settings.saveVadSegmentsOnly)
        assertFalse(settings.inlineEdit)
        assertEquals("https://120.126.151.159:56432/api/v2", settings.backendUrl)
        assertTrue(settings.enableTtsFeedback)
        assertEquals("", settings.selectedModelName)
        assertEquals("", settings.mobileModelSha256)
        assertEquals(null, settings.preferredAudioInputDeviceId)
        assertEquals(null, settings.preferredAudioOutputDeviceId)
        assertFalse(settings.allowAppAudioCapture)
        assertTrue(settings.preferCommunicationDeviceRouting)
    }

    @Test
    fun preferencesDefaultsMatchCanonicalUserSettingsDefaults() {
        val restoredSettings = preferencesToUserSettings(mutablePreferencesOf())
        val canonicalDefaults = UserSettings()

        assertEquals(canonicalDefaults, restoredSettings)
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
            booleanPreferencesKey("enable_tts_feedback") to true,
            stringPreferencesKey("selected_model_name") to "demo-model",
            stringPreferencesKey("mobile_model_sha256") to "abc123",
            intPreferencesKey("preferred_audio_input_device_id") to 101,
            intPreferencesKey("preferred_audio_output_device_id") to 202,
            booleanPreferencesKey("allow_app_audio_capture") to true,
            booleanPreferencesKey("prefer_communication_device_routing") to false
        )

        val settings = preferencesToUserSettings(preferences)

        assertEquals("tester", settings.userId)
        assertEquals(1234f, settings.lingerMs)
        assertEquals(678f, settings.partialIntervalMs)
        assertTrue(settings.saveVadSegmentsOnly)
        assertFalse(settings.inlineEdit)
        assertEquals("https://example.com", settings.backendUrl)
        assertTrue(settings.enableTtsFeedback)
        assertEquals("demo-model", settings.selectedModelName)
        assertEquals("abc123", settings.mobileModelSha256)
        assertEquals(101, settings.preferredAudioInputDeviceId)
        assertEquals(202, settings.preferredAudioOutputDeviceId)
        assertTrue(settings.allowAppAudioCapture)
        assertFalse(settings.preferCommunicationDeviceRouting)
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
            enableTtsFeedback = true,
            selectedModelName = "model-a",
            mobileModelSha256 = "deadbeef",
            preferredAudioInputDeviceId = 303,
            preferredAudioOutputDeviceId = 404,
            allowAppAudioCapture = true,
            preferCommunicationDeviceRouting = false
        )

        writeUserSettings(preferences, settings)

        assertEquals("writer", preferences[stringPreferencesKey("user_id")])
        assertEquals(1111f, preferences[floatPreferencesKey("linger_ms")])
        assertEquals(222f, preferences[floatPreferencesKey("partial_interval_ms")])
        assertEquals(true, preferences[booleanPreferencesKey("save_vad_segments_only")])
        assertEquals(false, preferences[booleanPreferencesKey("inline_edit")])
        assertEquals("https://backend.example.com", preferences[stringPreferencesKey("backend_url")])
        assertEquals(true, preferences[booleanPreferencesKey("enable_tts_feedback")])
        assertEquals("model-a", preferences[stringPreferencesKey("selected_model_name")])
        assertEquals("deadbeef", preferences[stringPreferencesKey("mobile_model_sha256")])
        assertEquals(303, preferences[intPreferencesKey("preferred_audio_input_device_id")])
        assertEquals(404, preferences[intPreferencesKey("preferred_audio_output_device_id")])
        assertEquals(true, preferences[booleanPreferencesKey("allow_app_audio_capture")])
        assertEquals(false, preferences[booleanPreferencesKey("prefer_communication_device_routing")])
    }

    @Test
    fun writeUserSettingsRemovesNullAudioRoutingSelections() {
        val preferences = mutablePreferencesOf(
            intPreferencesKey("preferred_audio_input_device_id") to 303,
            intPreferencesKey("preferred_audio_output_device_id") to 404
        )

        writeUserSettings(
            preferences,
            UserSettings(
                preferredAudioInputDeviceId = null,
                preferredAudioOutputDeviceId = null
            )
        )

        assertEquals(null, preferences[intPreferencesKey("preferred_audio_input_device_id")])
        assertEquals(null, preferences[intPreferencesKey("preferred_audio_output_device_id")])
    }
}
