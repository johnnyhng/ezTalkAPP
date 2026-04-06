package tw.com.johnnyhng.eztalk.asr.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel

private enum class SpeakerAsrTarget {
    EXPLORER,
    CONTENT
}

@Composable
fun SpeakerScreen(
    homeViewModel: HomeViewModel = viewModel()
) {
    val speakerViewModel: SpeakerViewModel = viewModel()
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val userSettings by homeViewModel.userSettings.collectAsState()
    val isAsrRecording by homeViewModel.isRecording.collectAsState()
    val countdownProgress by homeViewModel.countdownProgress.collectAsState()
    val isRecognizingSpeech by homeViewModel.isRecognizingSpeech.collectAsState()
    val isAsrModelLoading by homeViewModel.isAsrModelLoading.collectAsState()
    val uiState = speakerViewModel.uiState
    val selectedDocument = speakerViewModel.selectedDocument()
    val (playbackController, playbackState) = rememberSpeakerPlaybackController()
    var importTargetDirectory by remember { mutableStateOf<String?>(null) }
    var activeAsrTarget by rememberSaveable { mutableStateOf<SpeakerAsrTarget?>(null) }
    var explorerAsrText by rememberSaveable { mutableStateOf("") }
    var contentAsrText by rememberSaveable { mutableStateOf("") }

    val isSelectedDocumentPlaying = playbackController.isPlayingDocument(selectedDocument?.id)
    val isSelectedDocumentPaused = playbackController.isPausedDocument(selectedDocument?.id)
    val isExplorerWidgetVisible = uiState.directories.any { it.isExpanded }
    val isContentWidgetVisible = selectedDocument != null && !uiState.isEditingDocument
    val currentPlayingLineIndex =
        if (selectedDocument?.id == playbackState.playbackDocumentId || selectedDocument?.id == playbackState.currentPlayingDocumentId) {
            playbackState.currentPlayingLineIndex
        } else {
            null
        }

    LaunchedEffect(userSettings.userId) {
        speakerViewModel.setUserId(userSettings.userId)
    }

    LaunchedEffect(Unit) {
        homeViewModel.partialText.collect { text ->
            when (activeAsrTarget) {
                SpeakerAsrTarget.EXPLORER -> explorerAsrText = text
                SpeakerAsrTarget.CONTENT -> contentAsrText = text
                null -> Unit
            }
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.finalTranscript.collect { transcript ->
            when (activeAsrTarget) {
                SpeakerAsrTarget.EXPLORER -> explorerAsrText = transcript.recognizedText
                SpeakerAsrTarget.CONTENT -> contentAsrText = transcript.recognizedText
                null -> Unit
            }
        }
    }

    LaunchedEffect(isAsrRecording) {
        if (!isAsrRecording) {
            activeAsrTarget = null
        }
    }

    LaunchedEffect(isExplorerWidgetVisible, isContentWidgetVisible, activeAsrTarget, isAsrRecording) {
        val isTargetStillVisible = when (activeAsrTarget) {
            SpeakerAsrTarget.EXPLORER -> isExplorerWidgetVisible
            SpeakerAsrTarget.CONTENT -> isContentWidgetVisible
            null -> true
        }
        if (!isTargetStillVisible && isAsrRecording) {
            homeViewModel.toggleRecording()
        }
    }

    LaunchedEffect(selectedDocument?.id) {
        contentAsrText = ""
    }

    fun toggleSpeakerAsr(target: SpeakerAsrTarget) {
        if (isAsrRecording) {
            if (activeAsrTarget == target) {
                homeViewModel.toggleRecording()
            }
            return
        }
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
            return
        }
        scope.launch {
            homeViewModel.ensureSelectedModelInitialized()
            activeAsrTarget = target
            when (target) {
                SpeakerAsrTarget.EXPLORER -> explorerAsrText = ""
                SpeakerAsrTarget.CONTENT -> contentAsrText = ""
            }
            homeViewModel.startTranslateRecording()
        }
    }

    fun showImportResultToast(result: MultiTextImportResult) {
        when (result) {
            is MultiTextImportResult.Success -> {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.speaker_import_many_success,
                        result.folderName,
                        result.importedCount
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }

            MultiTextImportResult.NoFiles -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.speaker_import_no_txt),
                    Toast.LENGTH_SHORT
                ).show()
            }

            MultiTextImportResult.Failed -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.speaker_import_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val txtImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val targetDirectory = importTargetDirectory
        importTargetDirectory = null
        if (uris.isEmpty() || targetDirectory == null) return@rememberLauncherForActivityResult

        scope.launch {
            val result = speakerViewModel.importIntoFolder(
                context = context,
                sourceUris = uris,
                folderName = targetDirectory
            )
            showImportResultToast(result)
        }
    }

    val driveFilesImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val targetFolderName = sanitizeFolderName(uiState.driveImportFolderName)
        if (targetFolderName.isBlank()) return@rememberLauncherForActivityResult

        scope.launch {
            val result = speakerViewModel.importIntoFolder(
                context = context,
                sourceUris = uris,
                folderName = targetFolderName
            )
            showImportResultToast(result)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (uiState.showCreateFolderDialog) {
            AlertDialog(
                onDismissRequest = { speakerViewModel.dismissCreateFolderDialog() },
                title = {
                    Text(text = stringResource(R.string.speaker_create_folder))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.newFolderName,
                            onValueChange = { speakerViewModel.onNewFolderNameChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.speaker_folder_name_label)) },
                            placeholder = { Text(stringResource(R.string.speaker_folder_name_placeholder)) },
                            isError = uiState.createFolderDialogError != null
                        )
                        if (uiState.createFolderDialogError != null) {
                            Text(
                                text = uiState.createFolderDialogError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { speakerViewModel.createFolder() },
                        enabled = uiState.newFolderName.isNotBlank()
                    ) {
                        Text(text = stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { speakerViewModel.dismissCreateFolderDialog() }
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (uiState.isImporting) {
            AlertDialog(
                onDismissRequest = { },
                title = {
                    Text(text = stringResource(R.string.speaker_importing_title))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(
                                R.string.speaker_importing_target,
                                uiState.importProgressFolderName
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = if (uiState.importProgressTotal == 0) 0f
                            else uiState.importProgressCurrent.toFloat() / uiState.importProgressTotal.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(
                                R.string.speaker_importing_progress,
                                uiState.importProgressCurrent,
                                uiState.importProgressTotal
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {}
            )
        }

        if (uiState.showDriveImportDialog) {
            AlertDialog(
                onDismissRequest = { speakerViewModel.dismissDriveImportDialog() },
                title = {
                    Text(text = stringResource(R.string.speaker_google_drive))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.driveImportFolderName,
                            onValueChange = { speakerViewModel.onDriveImportFolderNameChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.speaker_import_target_folder_label)) },
                            placeholder = { Text(stringResource(R.string.speaker_import_target_folder_placeholder)) },
                            isError = uiState.driveImportDialogError != null
                        )
                        if (uiState.driveImportDialogError != null) {
                            Text(
                                text = uiState.driveImportDialogError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val targetFolder = speakerViewModel.confirmDriveImportFolder()
                            if (targetFolder != null) {
                                driveFilesImportLauncher.launch(arrayOf("text/plain"))
                            }
                        },
                        enabled = uiState.driveImportFolderName.isNotBlank()
                    ) {
                        Text(text = stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { speakerViewModel.dismissDriveImportDialog() }
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }

        SpeechFileExplorer(
            directories = uiState.directories,
            selectedDocumentId = uiState.selectedDocumentId,
            isLoading = uiState.isLoading,
            localAsrText = explorerAsrText,
            isLocalAsrRecording = activeAsrTarget == SpeakerAsrTarget.EXPLORER && isAsrRecording,
            localAsrCountdownProgress = if (activeAsrTarget == SpeakerAsrTarget.EXPLORER && isRecognizingSpeech) countdownProgress else 0f,
            isLocalAsrEnabled = !isAsrModelLoading && (!isAsrRecording || activeAsrTarget == SpeakerAsrTarget.EXPLORER),
            isImportEnabled = !uiState.isImporting,
            isDirectoryDeleteEnabled = !isSelectedDocumentPlaying && !isSelectedDocumentPaused,
            isDocumentDeleteEnabled = !isSelectedDocumentPlaying && !isSelectedDocumentPaused,
            onLocalAsrClick = { toggleSpeakerAsr(SpeakerAsrTarget.EXPLORER) },
            onCreateFolder = { speakerViewModel.showCreateFolderDialog() },
            onGoogleDriveImport = { speakerViewModel.showDriveImportDialog() },
            onToggleExpand = { directory -> speakerViewModel.toggleDirectory(directory) },
            onRefresh = { speakerViewModel.refreshDirectories() },
            onImportIntoDirectory = { directory ->
                importTargetDirectory = directory.displayName
                txtImportLauncher.launch(arrayOf("text/plain"))
            },
            onRemoveDirectory = { directory ->
                speakerViewModel.deleteFolder(directory)
            },
            onRemoveDocument = { document ->
                playbackController.stopIfPlaying(document.id)
                speakerViewModel.deleteDocument(document)
            },
            onDocumentSelected = { documentId ->
                speakerViewModel.onDocumentSelected(documentId)
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(0.42f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        SpeakerContentScreen(
            selectedDocument = selectedDocument,
            isTtsReady = playbackState.isReady,
            isPlaying = isSelectedDocumentPlaying,
            isPaused = isSelectedDocumentPaused,
            isEditing = uiState.isEditingDocument,
            localAsrText = contentAsrText,
            isLocalAsrRecording = activeAsrTarget == SpeakerAsrTarget.CONTENT && isAsrRecording,
            localAsrCountdownProgress = if (activeAsrTarget == SpeakerAsrTarget.CONTENT && isRecognizingSpeech) countdownProgress else 0f,
            isLocalAsrEnabled = !isAsrModelLoading && (!isAsrRecording || activeAsrTarget == SpeakerAsrTarget.CONTENT),
            currentPlayingLineIndex = currentPlayingLineIndex,
            editingText = uiState.editingText,
            onEditingTextChange = { speakerViewModel.onEditingTextChange(it) },
            onLocalAsrClick = { toggleSpeakerAsr(SpeakerAsrTarget.CONTENT) },
            onSpeakLine = { lineIndex, line ->
                if (selectedDocument == null) return@SpeakerContentScreen
                when (playbackController.playLine(selectedDocument, lineIndex, line)) {
                    SpeakerPlaybackResult.NOT_READY -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.speaker_tts_not_ready),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    SpeakerPlaybackResult.EMPTY_TEXT -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.speaker_empty_text_file),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    SpeakerPlaybackResult.STARTED -> Unit
                }
            },
            onPlay = {
                if (selectedDocument == null) return@SpeakerContentScreen
                when (playbackController.playDocument(selectedDocument)) {
                    SpeakerPlaybackResult.NOT_READY -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.speaker_tts_not_ready),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    SpeakerPlaybackResult.EMPTY_TEXT -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.speaker_empty_text_file),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    SpeakerPlaybackResult.STARTED -> Unit
                }
            },
            onPause = {
                playbackController.pause(selectedDocument?.id)
            },
            onStop = {
                playbackController.stop()
            },
            onEdit = {
                playbackController.stop()
                speakerViewModel.startEditing()
            },
            onSave = {
                speakerViewModel.saveEditing { saved ->
                    Toast.makeText(
                        context,
                        context.getString(
                            if (saved) R.string.speaker_save_success else R.string.speaker_save_failed
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onCancelEdit = {
                speakerViewModel.cancelEditing()
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(0.58f)
        )
    }
}
