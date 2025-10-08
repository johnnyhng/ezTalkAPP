package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.Model
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.ModelManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Create a DataStore instance at the top level
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Data class to hold the user's settings.
 */
data class UserSettings(
    val lingerMs: Float,
    val partialIntervalMs: Float,
    val saveVadSegmentsOnly: Boolean,
    val userId: String,
    val modelName: String?
)

/**
 * Manages loading and saving user settings using Jetpack DataStore.
 */
class SettingsManager(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        // Define keys for storing preferences
        val LINGER_MS_KEY = floatPreferencesKey("linger_ms")
        val PARTIAL_INTERVAL_MS_KEY = floatPreferencesKey("partial_interval_ms")
        val SAVE_VAD_SEGMENTS_ONLY_KEY = booleanPreferencesKey("save_vad_segments_only")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val MODEL_NAME_KEY = stringPreferencesKey("model_name")
    }

    // Flow to read the settings from DataStore, providing default values if none are set.
    val settingsFlow = appContext.dataStore.data.map { preferences ->
        val lingerMs = preferences[LINGER_MS_KEY] ?: 800f // Default: 800ms
        val partialIntervalMs = preferences[PARTIAL_INTERVAL_MS_KEY] ?: 500f // Default: 500ms
        val saveVadSegmentsOnly =
            preferences[SAVE_VAD_SEGMENTS_ONLY_KEY] ?: false // Default: false (Save Full Audio)
        val userId = preferences[USER_ID_KEY] ?: "user@gmail.com"
        val modelName = preferences[MODEL_NAME_KEY]
        UserSettings(lingerMs, partialIntervalMs, saveVadSegmentsOnly, userId, modelName)
    }

    // Functions to update the settings in DataStore. These are suspend functions.
    suspend fun updateLingerMs(value: Float) {
        appContext.dataStore.edit { settings ->
            settings[LINGER_MS_KEY] = value
        }
    }

    suspend fun updatePartialIntervalMs(value: Float) {
        appContext.dataStore.edit { settings ->
            settings[PARTIAL_INTERVAL_MS_KEY] = value
        }
    }

    suspend fun updateSaveVadSegmentsOnly(value: Boolean) {
        appContext.dataStore.edit { settings ->
            settings[SAVE_VAD_SEGMENTS_ONLY_KEY] = value
        }
    }

    suspend fun updateUserId(userId: String) {
        appContext.dataStore.edit { settings ->
            settings[USER_ID_KEY] = userId
        }
    }

    suspend fun updateModelName(modelName: String?) {
        appContext.dataStore.edit { settings ->
            if (modelName == null) {
                settings.remove(MODEL_NAME_KEY)
            } else {
                settings[MODEL_NAME_KEY] = modelName
            }
        }
    }
}

/**
 * ViewModel to hold and manage the UI state related to settings.
 * It survives configuration changes and provides data to the UI.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)

    // Expose the settings as a StateFlow so the UI can react to changes.
    val userSettings: StateFlow<UserSettings> = settingsManager.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserSettings(800f, 500f, false, "user@gmail.com", null) // Initial default values
    )

    var models by mutableStateOf<List<Model>>(emptyList())
        private set

    var selectedModel by mutableStateOf<Model?>(null)
        private set

    init {
        viewModelScope.launch {
            userSettings.collect { settings ->
                refreshModels(settings.userId)
                selectedModel = ModelManager.getModel(application, settings.userId, settings.modelName)
            }
        }
    }

    private fun refreshModels(userId: String) {
        models = ModelManager.listModels(getApplication(), userId)
    }

    fun updateLingerMs(value: Float) {
        viewModelScope.launch {
            settingsManager.updateLingerMs(value)
        }
    }

    fun updatePartialIntervalMs(value: Float) {
        viewModelScope.launch {
            settingsManager.updatePartialIntervalMs(value)
        }
    }

    fun updateSaveVadSegmentsOnly(value: Boolean) {
        viewModelScope.launch {
            settingsManager.updateSaveVadSegmentsOnly(value)
        }
    }

    fun updateUserId(userId: String) {
        viewModelScope.launch {
            settingsManager.updateUserId(userId)
            // When user ID changes, the selected model might not be valid anymore
            settingsManager.updateModelName(null) // Reset model selection
        }
    }

    fun updateModelName(modelName: String) {
        viewModelScope.launch {
            settingsManager.updateModelName(modelName)
        }
    }

    fun downloadModel(url: String) {
        // TODO: Implement model download logic
    }

    fun checkModelVersion() {
        // TODO: Implement version check logic
    }

    fun deleteModel(model: Model) {
        viewModelScope.launch {
            val currentUserId = userSettings.first().userId
            ModelManager.deleteModel(getApplication(), currentUserId, model.name)
            refreshModels(currentUserId) // Refresh the list after deletion

            // If the deleted model was the selected one, select the first available model.
            if (selectedModel?.name == model.name) {
                val firstModel = models.firstOrNull()
                settingsManager.updateModelName(firstModel?.name)
            }
        }
    }
}
