package tw.com.johnnyhng.eztalk.asr.managers

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.SimulateStreamingAsr
import tw.com.johnnyhng.eztalk.asr.audio.AudioRoutingRepository
import tw.com.johnnyhng.eztalk.asr.audio.AudioRoutingStatus
import tw.com.johnnyhng.eztalk.asr.sanitizeEntryScreenRoute
import tw.com.johnnyhng.eztalk.asr.data.classes.Model
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import tw.com.johnnyhng.eztalk.asr.utils.checkModelUpdate
import tw.com.johnnyhng.eztalk.asr.utils.sha256
import java.io.File

sealed class DownloadUiEvent {
    data class ShowToast(val message: String) : DownloadUiEvent()
}

class HomeViewModel @JvmOverloads constructor(
    application: Application,
    private val remoteModelRepository: RemoteModelRepository = DirectUrlRemoteModelRepository
) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    private val audioRoutingRepository = AudioRoutingRepository(application)
    
    val userSettings: StateFlow<UserSettings> = settingsManager.userSettings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UserSettings()
    )
    private val _audioRoutingStatus = MutableStateFlow(AudioRoutingStatus())
    val audioRoutingStatus = _audioRoutingStatus.asStateFlow()

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
    private val _remoteModels = mutableStateListOf<RemoteModelDescriptor>()
    val remoteModels: List<RemoteModelDescriptor> get() = _remoteModels
    var isFetchingRemoteModels by mutableStateOf(false)
    var remoteModelsErrorMessage by mutableStateOf<String?>(null)

    // Recognition Manager integration
    private val recognitionManager = RecognitionManager(application)
    private val recognizerInitMutex = Mutex()
    private var initializedModelKey: String? = null
    private var lastUserId: String? = null
    
    val isRecording = recognitionManager.isStarted
    val latestSamples = recognitionManager.latestSamples
    val isRecognizingSpeech = recognitionManager.isRecognizingSpeech
    val countdownProgress = recognitionManager.countdownProgress
    private val _isAsrModelLoading = MutableStateFlow(false)
    val isAsrModelLoading = _isAsrModelLoading.asStateFlow()

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
                if (lastUserId != null && lastUserId != settings.userId) {
                    initializedModelKey = null
                }
                lastUserId = settings.userId

                if (settings.selectedModelName.isNotBlank()) {
                    val model = _models.find { it.name == settings.selectedModelName }
                    if (model != null) {
                        _selectedModel.value = model
                    }
                }
                _audioRoutingStatus.value = audioRoutingRepository.getStatus(
                    selectedInputDeviceId = settings.preferredAudioInputDeviceId,
                    selectedOutputDeviceId = settings.preferredAudioOutputDeviceId
                )
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

    fun startTranslateRecording() {
        recognitionManager.startTranslate(userSettings.value)
    }

    fun startDataCollectRecording(dataCollectText: String) {
        recognitionManager.startDataCollect(userSettings.value, dataCollectText)
    }

    fun toggleRecording() {
        if (isRecording.value) {
            recognitionManager.stop()
        }
    }

    fun updateDataCollectText(text: String) {
        recognitionManager.updateDataCollectText(text)
    }

    suspend fun ensureSelectedModelInitialized() {
        val model = selectedModel ?: return
        val targetKey = buildModelKey(userSettings.value.userId, model.name)
        if (initializedModelKey == targetKey) return

        recognizerInitMutex.withLock {
            val latestModel = selectedModel ?: return
            val latestKey = buildModelKey(userSettings.value.userId, latestModel.name)
            if (initializedModelKey == latestKey) return

            _isAsrModelLoading.value = true
            try {
                SimulateStreamingAsr.initOfflineRecognizer(getApplication<Application>().assets, latestModel)
                initializedModelKey = latestKey
            } finally {
                _isAsrModelLoading.value = false
            }
        }
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
            initializedModelKey = null
            viewModelScope.launch {
                settingsManager.updateSettings(userSettings.value.copy(selectedModelName = name))
            }
        }
    }

    private fun buildModelKey(userId: String, modelName: String): String {
        return "$userId::$modelName"
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
        remoteModelsErrorMessage = null
        viewModelScope.launch {
            try {
                val selectedRemoteModelName = selectedModel?.name
                    ?: userSettings.value.selectedModelName.ifBlank { "custom-sense-voice" }
                val remoteModelResult = withContext(Dispatchers.IO) {
                    val fetchedResult = remoteModelRepository.listRemoteModels(
                        modelApiBaseUrl = userSettings.value.backendUrl,
                        userId = userSettings.value.userId,
                        selectedModelName = selectedRemoteModelName,
                        allowInsecureTls = userSettings.value.allowInsecureTls
                    )
                    markUpdateAvailability(
                        remoteModels = fetchedResult.models,
                        backendUrl = userSettings.value.backendUrl,
                        userId = userSettings.value.userId,
                        allowInsecureTls = userSettings.value.allowInsecureTls,
                        userModelsDir = getApplication<Application>().filesDir.resolve("models/${userSettings.value.userId}")
                    ).let { markedModels ->
                        RemoteModelListFetchResult(
                            models = markedModels,
                            errorMessage = fetchedResult.errorMessage
                        )
                    }
                }
                _remoteModels.clear()
                _remoteModels.addAll(remoteModelResult.models)
                remoteModelsErrorMessage = remoteModelResult.errorMessage
            } finally {
                isFetchingRemoteModels = false
            }
        }
    }

    fun downloadModel(remoteModel: RemoteModelDescriptor) {
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = null
            val result = withContext(Dispatchers.IO) {
                remoteModelRepository.downloadModel(
                    modelApiBaseUrl = userSettings.value.backendUrl,
                    userId = userSettings.value.userId,
                    remoteModel = remoteModel,
                    userModelsDir = getApplication<Application>().filesDir.resolve("models/${userSettings.value.userId}"),
                    allowInsecureTls = userSettings.value.allowInsecureTls,
                    onProgress = { progress -> _downloadProgress.value = progress }
                )
            }
            _isDownloading.value = false
            _downloadProgress.value = null

            result.fold(
                onSuccess = {
                    _downloadEventFlow.emit(DownloadUiEvent.ShowToast("Download complete: ${remoteModel.name}"))
                    loadModels()
                },
                onFailure = { error ->
                    _downloadEventFlow.emit(
                        DownloadUiEvent.ShowToast(
                            error.message ?: "Download failed: ${remoteModel.name}"
                        )
                    )
                }
            )
        }
    }

    fun updateLingerMs(v: Float) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(lingerMs = v)) }
    fun updatePartialIntervalMs(v: Float) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(partialIntervalMs = v)) }
    fun updateSaveVadSegmentsOnly(v: Boolean) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(saveVadSegmentsOnly = v)) }
    fun updateInlineEdit(v: Boolean) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(inlineEdit = v)) }
    fun updateBackendUrl(v: String) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(backendUrl = v)) }
    fun updateAllowInsecureTls(v: Boolean) = viewModelScope.launch {
        settingsManager.updateSettings(userSettings.value.copy(allowInsecureTls = v))
    }
    fun updateEnableTtsFeedback(v: Boolean) = viewModelScope.launch { settingsManager.updateSettings(userSettings.value.copy(enableTtsFeedback = v)) }
    fun updateEntryScreenRoute(v: String) = viewModelScope.launch {
        settingsManager.updateSettings(userSettings.value.copy(entryScreenRoute = sanitizeEntryScreenRoute(v)))
    }
    fun updateGeminiModel(v: String) = viewModelScope.launch {
        settingsManager.updateSettings(userSettings.value.copy(geminiModel = v))
    }
    fun updatePreferredAudioInputDeviceId(v: Int?) = viewModelScope.launch {
        settingsManager.updateSettings(userSettings.value.copy(preferredAudioInputDeviceId = v))
    }
    fun updatePreferredAudioOutputDeviceId(v: Int?) = viewModelScope.launch {
        settingsManager.updateSettings(userSettings.value.copy(preferredAudioOutputDeviceId = v))
    }
    fun updateAllowAppAudioCapture(v: Boolean) = viewModelScope.launch {
        settingsManager.updateSettings(userSettings.value.copy(allowAppAudioCapture = v))
    }
    fun updatePreferCommunicationDeviceRouting(v: Boolean) = viewModelScope.launch {
        settingsManager.updateSettings(userSettings.value.copy(preferCommunicationDeviceRouting = v))
    }
    fun refreshAudioRoutingStatus() {
        _audioRoutingStatus.value = audioRoutingRepository.getStatus(
            selectedInputDeviceId = userSettings.value.preferredAudioInputDeviceId,
            selectedOutputDeviceId = userSettings.value.preferredAudioOutputDeviceId
        )
    }

    private fun markUpdateAvailability(
        remoteModels: List<RemoteModelDescriptor>,
        backendUrl: String,
        userId: String,
        allowInsecureTls: Boolean,
        userModelsDir: File
    ): List<RemoteModelDescriptor> {
        if (backendUrl.isBlank() || userId.isBlank()) return remoteModels

        val mobileModel = remoteModels.firstOrNull { it.name == "mobile" } ?: return remoteModels
        val localModelFile = File(userModelsDir, "mobile/model.int8.onnx")
        if (!localModelFile.exists()) return remoteModels

        val localHash = sha256(localModelFile) ?: return remoteModels
        val remoteUpdate = checkModelUpdate(
            baseUrl = backendUrl,
            userId = userId,
            modelName = mobileModel.name,
            allowInsecureTls = allowInsecureTls
        ) ?: return remoteModels

        if (remoteUpdate.serverHash.isBlank()) return remoteModels

        return remoteModels.map { model ->
            if (model.name == "mobile") {
                model.copy(
                    serverHash = remoteUpdate.serverHash,
                    updateAvailable = !localHash.equals(remoteUpdate.serverHash, ignoreCase = true)
                )
            } else {
                model
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recognitionManager.stop()
    }
}
