package tw.com.johnnyhng.eztalk.asr.managers

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tw.com.johnnyhng.eztalk.asr.data.classes.Model
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings

sealed class DownloadUiEvent {
    data class ShowToast(val message: String) : DownloadUiEvent()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    
    val userSettings: StateFlow<UserSettings> = settingsManager.userSettings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UserSettings()
    )

    // Model Management
    private val _models = mutableStateListOf<Model>()
    val models: List<Model> get() = _models

    private val _selectedModel = MutableStateFlow<Model?>(null)
    val selectedModelFlow = _selectedModel.asStateFlow()
    val selectedModel: Model? get() = _selectedModel.value

    private val _showRemoteModelsDialog = MutableStateFlow(false)
    val showRemoteModelsDialog = _showRemoteModelsDialog.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloadingFlow = _isDownloading.asStateFlow()
    val isDownloading: Boolean get() = _isDownloading.value

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgressFlow = _downloadProgress.asStateFlow()
    val downloadProgress: Float? get() = _downloadProgress.value

    private val _downloadEventFlow = MutableSharedFlow<DownloadUiEvent>()
    val downloadEventFlow = _downloadEventFlow.asSharedFlow()

    val canDeleteModel: Boolean get() = _models.size > 1

    // Remote models for the dialog
    private val _remoteModels = mutableStateListOf<String>()
    val remoteModels: List<String> get() = _remoteModels
    var isFetchingRemoteModels by mutableStateOf(false)

    // Recognition Manager integration
    private val recognitionManager = RecognitionManager(application)
    
    val isRecording = recognitionManager.isStarted
    val latestSamples = recognitionManager.latestSamples
    val isRecognizingSpeech = recognitionManager.isRecognizingSpeech
    val countdownProgress = recognitionManager.countdownProgress

    private val _partialText = MutableSharedFlow<String>()
    val partialText = _partialText.asSharedFlow()

    private val _finalTranscript = MutableSharedFlow<Transcript>()
    val finalTranscript = _finalTranscript.asSharedFlow()

    init {
        loadModels()
        
        recognitionManager.onPartialResult = { text ->
            viewModelScope.launch { _partialText.emit(text) }
        }
        recognitionManager.onFinalResult = { transcript ->
            viewModelScope.launch { _finalTranscript.emit(transcript) }
        }

        // Sync selected model from settings
        viewModelScope.launch {
            userSettings.collectLatest { settings ->
                if (settings.selectedModelName.isNotBlank()) {
                    val model = _models.find { it.name == settings.selectedModelName }
                    if (model != null) {
                        _selectedModel.value = model
                    }
                }
            }
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            userSettings.collectLatest { settings ->
                val list = ModelManager.listModels(getApplication(), settings.userId)
                _models.clear()
                _models.addAll(list)
                
                // If no model selected yet, or selected model not in list, pick first or use settings
                if (_selectedModel.value == null || _models.none { it.name == _selectedModel.value?.name }) {
                    val savedModel = if (settings.selectedModelName.isNotBlank()) 
                        list.find { it.name == settings.selectedModelName } else null
                    _selectedModel.value = savedModel ?: list.firstOrNull()
                }
            }
        }
    }

    fun toggleRecording(isDataCollectMode: Boolean, dataCollectText: String) {
        if (isRecording.value) {
            recognitionManager.stop()
        } else {
            recognitionManager.start(userSettings.value, isDataCollectMode, dataCollectText)
        }
    }

    fun updateDataCollectText(text: String) {
        recognitionManager.updateDataCollectText(text)
    }

    // Settings & Model Methods
    fun updateUserId(userId: String) {
        viewModelScope.launch {
            settingsManager.updateSettings(userSettings.value.copy(userId = userId))
        }
    }

    fun updateModelName(name: String) {
        val model = _models.find { it.name == name }
        if (model != null) {
            _selectedModel.value = model
            viewModelScope.launch {
                settingsManager.updateSettings(userSettings.value.copy(selectedModelName = name))
            }
        }
    }

    fun updateModelUrl(url: String) {
        viewModelScope.launch {
            settingsManager.updateSettings(userSettings.value.copy(modelUrl = url))
        }
    }

    fun deleteModel(model: Model) {
        if (ModelManager.deleteModel(getApplication(), userSettings.value.userId, model.name)) {
            loadModels()
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
        isFetchingRemoteModels = true
        viewModelScope.launch {
            delay(1000)
            _remoteModels.clear()
            _remoteModels.add("v1.0-sense-voice")
            isFetchingRemoteModels = false
        }
    }

    fun downloadModel(modelName: String) {
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0f
            for (i in 1..100) {
                delay(50)
                _downloadProgress.value = i / 100f
            }
            _isDownloading.value = false
            _downloadProgress.value = null
            _downloadEventFlow.emit(DownloadUiEvent.ShowToast("Download complete: $modelName"))
            loadModels()
        }
    }

    fun updateLingerMs(v: Float) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(lingerMs = v)) }
    fun updatePartialIntervalMs(v: Float) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(partialIntervalMs = v)) }
    fun updateSaveVadSegmentsOnly(v: Boolean) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(saveVadSegmentsOnly = v)) }
    fun updateInlineEdit(v: Boolean) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(inlineEdit = v)) }
    fun updateBackendUrl(v: String) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(backendUrl = v)) }
    fun updateRecognitionUrl(v: String) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(recognitionUrl = v)) }
    fun updateEnableTtsFeedback(v: Boolean) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(enableTtsFeedback = v)) }

    override fun onCleared() {
        super.onCleared()
        recognitionManager.stop()
    }
}
