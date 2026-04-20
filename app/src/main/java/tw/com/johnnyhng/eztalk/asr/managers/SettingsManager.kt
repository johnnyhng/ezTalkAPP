package tw.com.johnnyhng.eztalk.asr.managers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tw.com.johnnyhng.eztalk.asr.sanitizeEntryScreenRoute
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val userIdKey = stringPreferencesKey("user_id")
private val lingerMsKey = floatPreferencesKey("linger_ms")
private val partialIntervalMsKey = floatPreferencesKey("partial_interval_ms")
private val saveVadSegmentsOnlyKey = booleanPreferencesKey("save_vad_segments_only")
private val inlineEditKey = booleanPreferencesKey("inline_edit")
private val backendUrlKey = stringPreferencesKey("backend_url")
private val allowInsecureTlsKey = booleanPreferencesKey("allow_insecure_tls")
private val enableTtsFeedbackKey = booleanPreferencesKey("enable_tts_feedback")
private val selectedModelNameKey = stringPreferencesKey("selected_model_name")
private val mobileModelSha256Key = stringPreferencesKey("mobile_model_sha256")
private val entryScreenRouteKey = stringPreferencesKey("entry_screen_route")
private val geminiModelKey = stringPreferencesKey("gemini_model")
private val speakerLlmExecutionModeKey = stringPreferencesKey("speaker_llm_execution_mode")
private val enableHomeLlmCorrectionKey = booleanPreferencesKey("enable_home_llm_correction")
private val enableHomeEnglishTranslationKey = booleanPreferencesKey("enable_home_english_translation")
private val enableTranslateLlmCorrectionKey = booleanPreferencesKey("enable_translate_llm_correction")
private val preferredAudioInputDeviceIdKey = intPreferencesKey("preferred_audio_input_device_id")
private val preferredAudioOutputDeviceIdKey = intPreferencesKey("preferred_audio_output_device_id")
private val allowAppAudioCaptureKey = booleanPreferencesKey("allow_app_audio_capture")
private val preferCommunicationDeviceRoutingKey =
    booleanPreferencesKey("prefer_communication_device_routing")
private val defaultUserSettings = UserSettings()

internal fun preferencesToUserSettings(preferences: Preferences): UserSettings {
    return UserSettings(
        userId = preferences[userIdKey] ?: defaultUserSettings.userId,
        lingerMs = preferences[lingerMsKey] ?: defaultUserSettings.lingerMs,
        partialIntervalMs = preferences[partialIntervalMsKey] ?: defaultUserSettings.partialIntervalMs,
        saveVadSegmentsOnly = preferences[saveVadSegmentsOnlyKey] ?: defaultUserSettings.saveVadSegmentsOnly,
        inlineEdit = preferences[inlineEditKey] ?: defaultUserSettings.inlineEdit,
        backendUrl = preferences[backendUrlKey] ?: defaultUserSettings.backendUrl,
        allowInsecureTls = preferences[allowInsecureTlsKey] ?: defaultUserSettings.allowInsecureTls,
        enableTtsFeedback = preferences[enableTtsFeedbackKey] ?: defaultUserSettings.enableTtsFeedback,
        selectedModelName = preferences[selectedModelNameKey] ?: defaultUserSettings.selectedModelName,
        mobileModelSha256 = preferences[mobileModelSha256Key] ?: defaultUserSettings.mobileModelSha256,
        entryScreenRoute = sanitizeEntryScreenRoute(
            preferences[entryScreenRouteKey] ?: defaultUserSettings.entryScreenRoute
        ),
        geminiModel = preferences[geminiModelKey] ?: defaultUserSettings.geminiModel,
        speakerLlmExecutionMode = preferences[speakerLlmExecutionModeKey]
            ?: defaultUserSettings.speakerLlmExecutionMode,
        enableHomeLlmCorrection = preferences[enableHomeLlmCorrectionKey]
            ?: defaultUserSettings.enableHomeLlmCorrection,
        enableHomeEnglishTranslation = preferences[enableHomeEnglishTranslationKey]
            ?: defaultUserSettings.enableHomeEnglishTranslation,
        enableTranslateLlmCorrection = preferences[enableTranslateLlmCorrectionKey]
            ?: defaultUserSettings.enableTranslateLlmCorrection,
        preferredAudioInputDeviceId = preferences[preferredAudioInputDeviceIdKey],
        preferredAudioOutputDeviceId = preferences[preferredAudioOutputDeviceIdKey],
        allowAppAudioCapture = preferences[allowAppAudioCaptureKey]
            ?: defaultUserSettings.allowAppAudioCapture,
        preferCommunicationDeviceRouting = preferences[preferCommunicationDeviceRoutingKey]
            ?: defaultUserSettings.preferCommunicationDeviceRouting
    )
}

internal fun writeUserSettings(preferences: MutablePreferences, settings: UserSettings) {
    preferences[userIdKey] = settings.userId
    preferences[lingerMsKey] = settings.lingerMs
    preferences[partialIntervalMsKey] = settings.partialIntervalMs
    preferences[saveVadSegmentsOnlyKey] = settings.saveVadSegmentsOnly
    preferences[inlineEditKey] = settings.inlineEdit
    preferences[backendUrlKey] = settings.backendUrl
    preferences[allowInsecureTlsKey] = settings.allowInsecureTls
    preferences[enableTtsFeedbackKey] = settings.enableTtsFeedback
    preferences[selectedModelNameKey] = settings.selectedModelName
    preferences[mobileModelSha256Key] = settings.mobileModelSha256
    preferences[entryScreenRouteKey] = sanitizeEntryScreenRoute(settings.entryScreenRoute)
    preferences[geminiModelKey] = settings.geminiModel
    preferences[speakerLlmExecutionModeKey] = settings.speakerLlmExecutionMode
    preferences[enableHomeLlmCorrectionKey] = settings.enableHomeLlmCorrection
    preferences[enableHomeEnglishTranslationKey] = settings.enableHomeEnglishTranslation
    preferences[enableTranslateLlmCorrectionKey] = settings.enableTranslateLlmCorrection
    if (settings.preferredAudioInputDeviceId != null) {
        preferences[preferredAudioInputDeviceIdKey] = settings.preferredAudioInputDeviceId
    } else {
        preferences.remove(preferredAudioInputDeviceIdKey)
    }
    if (settings.preferredAudioOutputDeviceId != null) {
        preferences[preferredAudioOutputDeviceIdKey] = settings.preferredAudioOutputDeviceId
    } else {
        preferences.remove(preferredAudioOutputDeviceIdKey)
    }
    preferences[allowAppAudioCaptureKey] = settings.allowAppAudioCapture
    preferences[preferCommunicationDeviceRoutingKey] = settings.preferCommunicationDeviceRouting
}

class SettingsManager(context: Context) {
    private val dataStore = context.dataStore

    val userSettings: Flow<UserSettings> = dataStore.data.map(::preferencesToUserSettings)

    suspend fun updateSettings(settings: UserSettings) {
        dataStore.edit { preferences ->
            writeUserSettings(preferences, settings)
        }
    }

    suspend fun updateMobileModelSha256(hash: String) {
        dataStore.edit { preferences ->
            preferences[mobileModelSha256Key] = hash
        }
    }
}
