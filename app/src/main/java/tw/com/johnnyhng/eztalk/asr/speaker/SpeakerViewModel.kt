package tw.com.johnnyhng.eztalk.asr.speaker

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import tw.com.johnnyhng.eztalk.asr.R

internal data class SpeakerScreenUiState(
    val directories: List<SpeakerDirectoryUi> = emptyList(),
    val selectedDocumentId: String? = null,
    val isLoading: Boolean = true,
    val showCreateFolderDialog: Boolean = false,
    val newFolderName: String = "",
    val createFolderDialogError: String? = null,
    val showDriveImportDialog: Boolean = false,
    val driveImportFolderName: String = "",
    val driveImportDialogError: String? = null,
    val isImporting: Boolean = false,
    val importProgressCurrent: Int = 0,
    val importProgressTotal: Int = 0,
    val importProgressFolderName: String = "",
    val isEditingDocument: Boolean = false,
    val editingText: String = "",
    val contentAsrText: String = "",
    val contentSemanticCandidateLineIndex: Int? = null,
    val llmFallbackState: SpeakerLlmFallbackState? = null
)

internal class SpeakerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SpeakerRepository(application)
    private var currentUserId: String? = null

    var uiState by mutableStateOf(SpeakerScreenUiState())
        private set

    fun setUserId(userId: String) {
        if (currentUserId == userId) return
        currentUserId = userId
        uiState = SpeakerScreenUiState()
        reloadDirectories()
    }

    fun selectedDocument(): SpeakerDocumentUi? {
        return uiState.directories
            .flatMap { it.documents }
            .firstOrNull { it.id == uiState.selectedDocumentId }
    }

    fun onDocumentSelected(documentId: String) {
        val document = uiState.directories
            .flatMap { it.documents }
            .firstOrNull { it.id == documentId }
        uiState = uiState.copy(
            selectedDocumentId = documentId,
            isEditingDocument = false,
            editingText = document?.fullText.orEmpty(),
            contentAsrText = "",
            contentSemanticCandidateLineIndex = null,
            llmFallbackState = null
        )
    }

    fun onEditingTextChange(text: String) {
        uiState = uiState.copy(editingText = text)
    }

    fun updateContentAsrText(text: String) {
        uiState = uiState.copy(contentAsrText = text)
    }

    fun updateLlmFallbackState(state: SpeakerLlmFallbackState?) {
        uiState = uiState.copy(llmFallbackState = state)
    }

    fun updateContentSemanticCandidateLineIndex(lineIndex: Int?) {
        uiState = uiState.copy(contentSemanticCandidateLineIndex = lineIndex)
    }

    fun resetContentSemanticUi() {
        uiState = uiState.copy(
            contentAsrText = "",
            contentSemanticCandidateLineIndex = null,
            llmFallbackState = null
        )
    }

    fun startEditing() {
        val document = selectedDocument() ?: return
        uiState = uiState.copy(
            isEditingDocument = true,
            editingText = document.fullText,
            contentAsrText = "",
            contentSemanticCandidateLineIndex = null,
            llmFallbackState = null
        )
    }

    fun cancelEditing() {
        uiState = uiState.copy(
            isEditingDocument = false,
            editingText = selectedDocument()?.fullText.orEmpty(),
            contentAsrText = "",
            contentSemanticCandidateLineIndex = null,
            llmFallbackState = null
        )
    }

    fun saveEditing(onComplete: (Boolean) -> Unit) {
        val userId = currentUserId ?: return
        val document = selectedDocument() ?: return
        val updatedText = uiState.editingText
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                repository.saveDocument(document.id, updatedText)
            }
            if (saved) {
                uiState = uiState.copy(isEditingDocument = false)
                reloadDirectories(userId)
            }
            onComplete(saved)
        }
    }

    fun toggleDirectory(directory: SpeakerDirectoryUi) {
        val nextExpanded = !directory.isExpanded
        val shouldClearSelection = nextExpanded ||
            directory.documents.any { it.id == uiState.selectedDocumentId }
        uiState = uiState.copy(
            directories = uiState.directories.map {
                when {
                    it.id == directory.id -> it.copy(isExpanded = nextExpanded)
                    nextExpanded -> it.copy(isExpanded = false)
                    else -> it
                }
            },
            selectedDocumentId = if (shouldClearSelection) null else uiState.selectedDocumentId,
            isEditingDocument = if (shouldClearSelection) false else uiState.isEditingDocument,
            editingText = if (shouldClearSelection) "" else uiState.editingText,
            contentAsrText = if (shouldClearSelection) "" else uiState.contentAsrText,
            contentSemanticCandidateLineIndex = if (shouldClearSelection) null else uiState.contentSemanticCandidateLineIndex,
            llmFallbackState = if (shouldClearSelection) null else uiState.llmFallbackState
        )
    }

    fun showCreateFolderDialog() {
        uiState = uiState.copy(showCreateFolderDialog = true)
    }

    fun dismissCreateFolderDialog() {
        uiState = uiState.copy(
            showCreateFolderDialog = false,
            newFolderName = "",
            createFolderDialogError = null
        )
    }

    fun onNewFolderNameChanged(value: String) {
        uiState = uiState.copy(
            newFolderName = value,
            createFolderDialogError = null
        )
    }

    fun createFolder() {
        val userId = currentUserId ?: return
        val context = getApplication<Application>()
        val sanitizedName = sanitizeFolderName(uiState.newFolderName)
        if (sanitizedName.isBlank()) {
            uiState = uiState.copy(
                createFolderDialogError = context.getString(R.string.speaker_invalid_folder_name)
            )
            return
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.createFolder(userId, sanitizedName)
            }
            when (result) {
                FolderCreationResult.CREATED -> {
                    uiState = uiState.copy(
                        newFolderName = "",
                        showCreateFolderDialog = false,
                        createFolderDialogError = null
                    )
                    reloadDirectories(userId)
                }
                FolderCreationResult.ALREADY_EXISTS -> {
                    uiState = uiState.copy(
                        createFolderDialogError = context.getString(R.string.speaker_folder_exists, sanitizedName)
                    )
                }
                FolderCreationResult.FAILED -> {
                    uiState = uiState.copy(
                        createFolderDialogError = context.getString(R.string.speaker_create_folder_failed)
                    )
                }
            }
        }
    }

    fun showDriveImportDialog() {
        uiState = uiState.copy(showDriveImportDialog = true)
    }

    fun dismissDriveImportDialog() {
        uiState = uiState.copy(
            showDriveImportDialog = false,
            driveImportFolderName = "",
            driveImportDialogError = null
        )
    }

    fun onDriveImportFolderNameChanged(value: String) {
        uiState = uiState.copy(
            driveImportFolderName = value,
            driveImportDialogError = null
        )
    }

    fun confirmDriveImportFolder(): String? {
        val context = getApplication<Application>()
        val sanitizedName = sanitizeFolderName(uiState.driveImportFolderName)
        if (sanitizedName.isBlank()) {
            uiState = uiState.copy(
                driveImportDialogError = context.getString(R.string.speaker_invalid_folder_name)
            )
            return null
        }
        uiState = uiState.copy(
            driveImportFolderName = sanitizedName,
            driveImportDialogError = null,
            showDriveImportDialog = false
        )
        return sanitizedName
    }

    suspend fun importIntoFolder(
        context: Context,
        sourceUris: List<android.net.Uri>,
        folderName: String
    ): MultiTextImportResult {
        val userId = currentUserId ?: return MultiTextImportResult.Failed
        uiState = uiState.copy(
            isImporting = true,
            importProgressCurrent = 0,
            importProgressTotal = sourceUris.size,
            importProgressFolderName = folderName
        )
        val result = withContext(Dispatchers.IO) {
            importTextUrisIntoSpeakerFolder(
                context = context,
                sourceUris = sourceUris,
                filesDir = context.filesDir,
                userId = userId,
                folderName = folderName,
                onProgress = { current, total ->
                    uiState = uiState.copy(
                        importProgressCurrent = current,
                        importProgressTotal = total
                    )
                }
            )
        }
        uiState = uiState.copy(isImporting = false)
        if (result is MultiTextImportResult.Success) {
            reloadDirectories(userId)
        }
        return result
    }

    fun deleteFolder(directory: SpeakerDirectoryUi) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteFolder(userId, directory.displayName)
            }
            val updatedDirectories = uiState.directories.filterNot { it.id == directory.id }
            uiState = uiState.copy(directories = updatedDirectories)
            setSelectedDocumentIfNeeded(updatedDirectories)
        }
    }

    fun deleteDocument(document: SpeakerDocumentUi) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteDocument(document.id)
            }
            reloadDirectories()
        }
    }

    fun refreshDirectories() {
        reloadDirectories()
    }

    private fun reloadDirectories(userId: String? = currentUserId) {
        val resolvedUserId = userId ?: return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            val loadedDirectories = withContext(Dispatchers.IO) {
                repository.loadDirectories(resolvedUserId, uiState.directories)
            }
            uiState = uiState.copy(
                directories = loadedDirectories,
                isLoading = false
            )
            setSelectedDocumentIfNeeded(loadedDirectories)
            if (!uiState.isEditingDocument) {
                uiState = uiState.copy(editingText = selectedDocument()?.fullText.orEmpty())
            }
        }
    }

    private fun setSelectedDocumentIfNeeded(updatedDirectories: List<SpeakerDirectoryUi>) {
        val allDocuments = updatedDirectories.flatMap { it.documents }
        val nextSelectedDocumentId = when {
            allDocuments.isEmpty() -> null
            allDocuments.any { it.id == uiState.selectedDocumentId } -> uiState.selectedDocumentId
            else -> null
        }
        uiState = uiState.copy(
            selectedDocumentId = nextSelectedDocumentId,
            editingText = if (nextSelectedDocumentId == null) "" else uiState.editingText,
            isEditingDocument = if (nextSelectedDocumentId == null) false else uiState.isEditingDocument
        )
    }
}
