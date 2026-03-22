package tw.com.johnnyhng.eztalk.asr.managers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(context: Context) {
    private val dataStore = context.dataStore

    val userSettings: Flow<UserSettings> = dataStore.data.map { preferences ->
        UserSettings(
            userId = preferences[stringPreferencesKey("user_id")] ?: "default_user",
            lingerMs = preferences[floatPreferencesKey("linger_ms")] ?: 1000f,
            partialIntervalMs = preferences[floatPreferencesKey("partial_interval_ms")] ?: 500f,
            saveVadSegmentsOnly = preferences[booleanPreferencesKey("save_vad_segments_only")] ?: false,
            inlineEdit = preferences[booleanPreferencesKey("inline_edit")] ?: true,
            backendUrl = preferences[stringPreferencesKey("backend_url")] ?: "https://120.126.151.159:56432",
            recognitionUrl = preferences[stringPreferencesKey("recognition_url")] ?: "",
            enableTtsFeedback = preferences[booleanPreferencesKey("enable_tts_feedback")] ?: false,
            modelUrl = preferences[stringPreferencesKey("model_url")] ?: "",
            selectedModelName = preferences[stringPreferencesKey("selected_model_name")] ?: ""
        )
    }

    suspend fun updateSettings(settings: UserSettings) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("user_id")] = settings.userId
            preferences[floatPreferencesKey("linger_ms")] = settings.lingerMs
            preferences[floatPreferencesKey("partial_interval_ms")] = settings.partialIntervalMs
            preferences[booleanPreferencesKey("save_vad_segments_only")] = settings.saveVadSegmentsOnly
            preferences[booleanPreferencesKey("inline_edit")] = settings.inlineEdit
            preferences[stringPreferencesKey("backend_url")] = settings.backendUrl
            preferences[stringPreferencesKey("recognition_url")] = settings.recognitionUrl
            preferences[booleanPreferencesKey("enable_tts_feedback")] = settings.enableTtsFeedback
            preferences[stringPreferencesKey("model_url")] = settings.modelUrl
            preferences[stringPreferencesKey("selected_model_name")] = settings.selectedModelName
        }
    }
}
