package com.k2fsa.sherpa.onnx.simulate.streaming.asr.managers

import android.app.Application
import android.content.Context
import android.util.Log
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
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.data.classes.Model
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.managers.ModelManager
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

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
    val modelName: String?,
    val modelUrl: String,
    val backendUrl: String,
    val inlineEdit: Boolean
) {
    val recognitionUrl: String
        get() = if (backendUrl.isNotBlank()) "$backendUrl/api/process_audio" else ""
}

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
        val MODEL_URL_KEY = stringPreferencesKey("model_url")
        val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
        val INLINE_EDIT_KEY = booleanPreferencesKey("inline_edit")
    }

    // Flow to read the settings from DataStore, providing default values if none are set.
    val settingsFlow = appContext.dataStore.data.map { preferences ->
        val lingerMs = preferences[LINGER_MS_KEY] ?: 800f // Default: 800ms
        val partialIntervalMs = preferences[PARTIAL_INTERVAL_MS_KEY] ?: 500f // Default: 500ms
        val saveVadSegmentsOnly =
            preferences[SAVE_VAD_SEGMENTS_ONLY_KEY] ?: false // Default: false (Save Full Audio)
        val userId = preferences[USER_ID_KEY] ?: "user@example.com"
        val modelName = preferences[MODEL_NAME_KEY]
        val modelUrl = preferences[MODEL_URL_KEY] ?: ""
        val backendUrl = preferences[BACKEND_URL_KEY] ?: "https://120.126.151.159:56432"
        val inlineEdit = preferences[INLINE_EDIT_KEY] ?: false
        UserSettings(
            lingerMs,
            partialIntervalMs,
            saveVadSegmentsOnly,
            userId,
            modelName,
            modelUrl,
            backendUrl,
            inlineEdit
        )
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

    suspend fun updateModelUrl(url: String) {
        appContext.dataStore.edit { settings ->
            settings[MODEL_URL_KEY] = url
        }
    }

    suspend fun updateBackendUrl(url: String) {
        appContext.dataStore.edit { settings ->
            settings[BACKEND_URL_KEY] = url
        }
    }

    suspend fun updateInlineEdit(inlineEdit: Boolean) {
        appContext.dataStore.edit { settings ->
            settings[INLINE_EDIT_KEY] = inlineEdit
        }
    }
}

sealed class DownloadUiEvent {
    data class ShowToast(val message: String) : DownloadUiEvent()
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
        initialValue = UserSettings(800f, 500f, false, "user@example.com", null, "", "https://120.126.151.159:56432", false) // Initial default values
    )

    var models by mutableStateOf<List<Model>>(emptyList())
        private set

    var selectedModel by mutableStateOf<Model?>(null)
        private set

    private val _showRemoteModelsDialog = MutableStateFlow(false)
    val showRemoteModelsDialog = _showRemoteModelsDialog.asStateFlow()

    var remoteModels by mutableStateOf<List<String>>(emptyList())
        private set

    var isFetchingRemoteModels by mutableStateOf(false)
        private set


    val canDeleteModel: Boolean
        get() = models.size > 1

    var isDownloading by mutableStateOf(false)
        private set

    var downloadProgress: Float? by mutableStateOf(null)
        private set

    private val _downloadEventChannel = Channel<DownloadUiEvent>()
    val downloadEventFlow = _downloadEventChannel.receiveAsFlow()

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

    fun updateModelUrl(url: String) {
        viewModelScope.launch {
            settingsManager.updateModelUrl(url)
        }
    }

    fun updateBackendUrl(url: String) {
        viewModelScope.launch {
            settingsManager.updateBackendUrl(url)
        }
    }

    fun updateInlineEdit(inlineEdit: Boolean) {
        viewModelScope.launch {
            settingsManager.updateInlineEdit(inlineEdit)
        }
    }

    fun showRemoteModelsDialog() {
        _showRemoteModelsDialog.value = true
        fetchRemoteModels()
    }

    fun dismissRemoteModelsDialog() {
        _showRemoteModelsDialog.value = false
    }

    private fun fetchRemoteModels() {
        viewModelScope.launch {
            isFetchingRemoteModels = true
            val modelUrl = userSettings.value.modelUrl
            val userId = userSettings.value.userId
            if (modelUrl.isBlank() || userId.isBlank()) {
                isFetchingRemoteModels = false
                return@launch
            }
            try {
                val models = withContext(Dispatchers.IO) {
                    val url = URL("$modelUrl/api/model/list/$userId")
                    val connection = url.openConnection() as HttpURLConnection
                    val modelsJson = connection.inputStream.bufferedReader().readText()
                    val jsonArray = JSONArray(modelsJson)
                    List(jsonArray.length()) { i -> jsonArray.getString(i) }
                }
                remoteModels = models
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch remote models", e)
                remoteModels = emptyList()
            } finally {
                isFetchingRemoteModels = false
            }
        }
    }

    fun downloadModel(modelName: String) {
        val url = "${userSettings.value.modelUrl}/files"
        val userId = userSettings.value.userId
        if (url.isBlank()) {
            Log.w(TAG, "Download URL is blank.")
            return
        }

        viewModelScope.launch {
            isDownloading = true
            downloadProgress = null
            val success = try {
                withContext(Dispatchers.IO) {
                    val targetDir = File(getApplication<Application>().filesDir, "models/$userId/$modelName")
                    if (!targetDir.exists()) {
                        targetDir.mkdirs()
                    }

                    val modelUrl = URL("$url/users/$userId/models/$modelName/model.int8.onnx")
                    val tokensUrl = URL("$url/users/$userId/models/$modelName/tokens.txt")

                    val modelConn = modelUrl.openConnection()
                    val modelSize = modelConn.contentLengthLong

                    val tokensConn = tokensUrl.openConnection()
                    val tokensSize = tokensConn.contentLengthLong

                    val totalSize = if (modelSize > 0 && tokensSize > 0) modelSize + tokensSize else -1L
                    var downloadedBytes = 0L

                    if (totalSize > 0) {
                        downloadProgress = 0f
                    }

                    Log.i(TAG, "Downloading model from $modelUrl")
                    downloadFile(modelConn.inputStream, File(targetDir, "model.int8.onnx")) { bytesRead ->
                        if (totalSize > 0) {
                            downloadedBytes += bytesRead
                            downloadProgress = downloadedBytes.toFloat() / totalSize
                        }
                    }

                    Log.i(TAG, "Downloading tokens from $tokensUrl")
                    downloadFile(tokensConn.inputStream, File(targetDir, "tokens.txt")) { bytesRead ->
                        if (totalSize > 0) {
                            downloadedBytes += bytesRead
                            downloadProgress = downloadedBytes.toFloat() / totalSize
                        }
                    }
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed", e)
                false
            }

            if (success) {
                Log.i(TAG, "Download finished, refreshing models.")
                refreshModels(userId)
                _downloadEventChannel.send(DownloadUiEvent.ShowToast("Download successful!"))
            } else {
                _downloadEventChannel.send(DownloadUiEvent.ShowToast("Download failed. Please check URL and connection."))
            }
            isDownloading = false
            downloadProgress = null
        }
    }

    private fun downloadFile(inputStream: InputStream, outputFile: File, onChunkRead: (bytesRead: Int) -> Unit) {
        FileOutputStream(outputFile).use { output ->
            inputStream.use { input ->
                val buffer = ByteArray(4 * 1024) // 4KB buffer
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    onChunkRead(bytesRead)
                }
            }
        }
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
