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
private val enableTtsFeedbackKey = booleanPreferencesKey("enable_tts_feedback")
private val selectedModelNameKey = stringPreferencesKey("selected_model_name")
private val entryScreenRouteKey = stringPreferencesKey("entry_screen_route")
private val geminiModelKey = stringPreferencesKey("gemini_model")
private val defaultUserSettings = UserSettings()

internal fun preferencesToUserSettings(preferences: Preferences): UserSettings {
    return UserSettings(
        userId = preferences[userIdKey] ?: defaultUserSettings.userId,
        lingerMs = preferences[lingerMsKey] ?: defaultUserSettings.lingerMs,
        partialIntervalMs = preferences[partialIntervalMsKey] ?: defaultUserSettings.partialIntervalMs,
        saveVadSegmentsOnly = preferences[saveVadSegmentsOnlyKey] ?: defaultUserSettings.saveVadSegmentsOnly,
        inlineEdit = preferences[inlineEditKey] ?: defaultUserSettings.inlineEdit,
        backendUrl = preferences[backendUrlKey] ?: defaultUserSettings.backendUrl,
        enableTtsFeedback = preferences[enableTtsFeedbackKey] ?: defaultUserSettings.enableTtsFeedback,
        selectedModelName = preferences[selectedModelNameKey] ?: defaultUserSettings.selectedModelName,
        entryScreenRoute = sanitizeEntryScreenRoute(
            preferences[entryScreenRouteKey] ?: defaultUserSettings.entryScreenRoute
        ),
        geminiModel = preferences[geminiModelKey] ?: defaultUserSettings.geminiModel
    )
}

internal fun writeUserSettings(preferences: MutablePreferences, settings: UserSettings) {
    preferences[userIdKey] = settings.userId
    preferences[lingerMsKey] = settings.lingerMs
    preferences[partialIntervalMsKey] = settings.partialIntervalMs
    preferences[saveVadSegmentsOnlyKey] = settings.saveVadSegmentsOnly
    preferences[inlineEditKey] = settings.inlineEdit
    preferences[backendUrlKey] = settings.backendUrl
    preferences[enableTtsFeedbackKey] = settings.enableTtsFeedback
    preferences[selectedModelNameKey] = settings.selectedModelName
    preferences[entryScreenRouteKey] = sanitizeEntryScreenRoute(settings.entryScreenRoute)
    preferences[geminiModelKey] = settings.geminiModel
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
