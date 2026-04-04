package tw.com.johnnyhng.eztalk.asr.managers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val userIdKey = stringPreferencesKey("user_id")
private val lingerMsKey = floatPreferencesKey("linger_ms")
private val partialIntervalMsKey = floatPreferencesKey("partial_interval_ms")
private val saveVadSegmentsOnlyKey = booleanPreferencesKey("save_vad_segments_only")
private val inlineEditKey = booleanPreferencesKey("inline_edit")
private val backendUrlKey = stringPreferencesKey("backend_url")
private val recognitionUrlKey = stringPreferencesKey("recognition_url")
private val enableTtsFeedbackKey = booleanPreferencesKey("enable_tts_feedback")
private val modelUrlKey = stringPreferencesKey("model_url")
private val selectedModelNameKey = stringPreferencesKey("selected_model_name")

internal fun preferencesToUserSettings(preferences: Preferences): UserSettings {
    return UserSettings(
        userId = preferences[userIdKey] ?: "default_user",
        lingerMs = preferences[lingerMsKey] ?: 5000f,
        partialIntervalMs = preferences[partialIntervalMsKey] ?: 500f,
        saveVadSegmentsOnly = preferences[saveVadSegmentsOnlyKey] ?: false,
        inlineEdit = preferences[inlineEditKey] ?: false,
        backendUrl = preferences[backendUrlKey] ?: "https://120.126.151.159:56432/api/v2",
        recognitionUrl = preferences[recognitionUrlKey] ?: "",
        enableTtsFeedback = preferences[enableTtsFeedbackKey] ?: true,
        modelUrl = preferences[modelUrlKey] ?: "",
        selectedModelName = preferences[selectedModelNameKey] ?: ""
    )
}

internal fun writeUserSettings(preferences: MutablePreferences, settings: UserSettings) {
    preferences[userIdKey] = settings.userId
    preferences[lingerMsKey] = settings.lingerMs
    preferences[partialIntervalMsKey] = settings.partialIntervalMs
    preferences[saveVadSegmentsOnlyKey] = settings.saveVadSegmentsOnly
    preferences[inlineEditKey] = settings.inlineEdit
    preferences[backendUrlKey] = settings.backendUrl
    preferences[recognitionUrlKey] = settings.recognitionUrl
    preferences[enableTtsFeedbackKey] = settings.enableTtsFeedback
    preferences[modelUrlKey] = settings.modelUrl
    preferences[selectedModelNameKey] = settings.selectedModelName
}

class SettingsManager(context: Context) {
    private val dataStore = context.dataStore

    val userSettings: Flow<UserSettings> = dataStore.data.map(::preferencesToUserSettings)

    suspend fun updateSettings(settings: UserSettings) {
        dataStore.edit { preferences ->
            writeUserSettings(preferences, settings)
        }
    }
}
