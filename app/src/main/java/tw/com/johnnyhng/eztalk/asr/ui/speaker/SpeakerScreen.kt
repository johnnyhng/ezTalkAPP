package tw.com.johnnyhng.eztalk.asr.ui.speaker

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.llm.GoogleAuthGeminiAccessTokenProvider
import tw.com.johnnyhng.eztalk.asr.llm.GeminiLlmProvider
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.speaker.MultiTextImportResult
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerContentCommand
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerLlmFallbackState
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerNoMatchOutcome
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerSemanticDecision
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerSemanticModule
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerPlaybackResult
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerRemoteFolder
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerSearchResult
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerSemanticIndexer
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerSyncDirection
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerViewModel
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerDocumentUi
import tw.com.johnnyhng.eztalk.asr.speaker.buildSpeakerContentLines
import tw.com.johnnyhng.eztalk.asr.speaker.handleSpeakerContentAsr
import tw.com.johnnyhng.eztalk.asr.speaker.importTextUrisIntoSpeakerFolder
import tw.com.johnnyhng.eztalk.asr.speaker.rememberSpeakerAsrController
import tw.com.johnnyhng.eztalk.asr.speaker.rememberSpeakerPlaybackController
import tw.com.johnnyhng.eztalk.asr.speaker.sanitizeFolderName
import tw.com.johnnyhng.eztalk.asr.speaker.toFallbackState
import tw.com.johnnyhng.eztalk.asr.speaker.toastMessageResId

private enum class SpeakerAsrTarget {
    EXPLORER,
    CONTENT
}

private enum class SpeakerExpandedPane {
    EXPLORER,
    CONTENT
}

@Composable
fun SpeakerScreen(
    homeViewModel: HomeViewModel = viewModel()
) {
    val speakerViewModel: SpeakerViewModel = viewModel()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val userSettings by homeViewModel.userSettings.collectAsState()
    val isAsrModelLoading by homeViewModel.isAsrModelLoading.collectAsState()
    val uiState = speakerViewModel.uiState
    val selectedDocument = speakerViewModel.selectedDocument()
    val (playbackController, playbackState) = rememberSpeakerPlaybackController()
    val (speakerAsrController, speakerAsrState) = rememberSpeakerAsrController()
    var importTargetDirectory by remember { mutableStateOf<String?>(null) }
    var activeAsrTarget by rememberSaveable { mutableStateOf<SpeakerAsrTarget?>(null) }
    var explorerAsrText by rememberSaveable { mutableStateOf("") }
    var lastHandledContentFinalVersion by rememberSaveable { mutableStateOf(0) }
    var expandedPane by rememberSaveable { mutableStateOf(SpeakerExpandedPane.EXPLORER) }
    var isContentSpeechModeEnabled by rememberSaveable { mutableStateOf(false) }
    var selectedRemoteFolderIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var isImportProgressDialogVisible by rememberSaveable { mutableStateOf(false) }
    var isSyncProgressDialogVisible by rememberSaveable { mutableStateOf(false) }
    var pendingCloudUploadDirectory by rememberSaveable { mutableStateOf<String?>(null) }
    val semanticIndexer = remember { SpeakerSemanticIndexer() }
    val geminiModel = userSettings.geminiModel.takeUnless { it.equals("none", ignoreCase = true) }
    val semanticModule = remember(appContext, userSettings.geminiModel) {
        SpeakerSemanticModule(
            llmProvider = geminiModel?.let {
                GeminiLlmProvider(
                    accessTokenProvider = GoogleAuthGeminiAccessTokenProvider(appContext)
                )
            },
            llmModel = geminiModel ?: "gemini-2.5-flash"
        )
    }
    val orderedDocuments = remember(uiState.directories) {
        uiState.directories.flatMap { it.documents }
    }
    val selectedDocumentIndex = orderedDocuments.indexOfFirst { it.id == selectedDocument?.id }
    val previousDocument = orderedDocuments.getOrNull(selectedDocumentIndex - 1)
    val nextDocument = orderedDocuments.getOrNull(selectedDocumentIndex + 1)
    val indexedSelectedDocumentChunks = remember(selectedDocument?.id, selectedDocument?.fullText) {
        selectedDocument?.let(semanticIndexer::indexDocument).orEmpty()
    }

    val isSelectedDocumentPlaying = playbackController.isPlayingDocument(selectedDocument?.id)
    val isSelectedDocumentPaused = playbackController.isPausedDocument(selectedDocument?.id)
    val isAnyTtsPlaying = playbackState.currentPlayingDocumentId != null
    val isExplorerWidgetVisible = uiState.directories.any { it.isExpanded }
    val isContentWidgetVisible = selectedDocument != null && !uiState.isEditingDocument
    val currentPlayingLineIndex =
        if (selectedDocument?.id == playbackState.playbackDocumentId || selectedDocument?.id == playbackState.currentPlayingDocumentId) {
            playbackState.currentPlayingLineIndex
        } else {
            null
        }
    val cloudStatusText = if (uiState.isCloudSignedIn) {
        context.getString(
            R.string.speaker_cloud_status_signed_in,
            uiState.cloudUserId.orEmpty()
        )
    } else {
        context.getString(R.string.speaker_cloud_status_signed_out)
    }
    val playDocumentWithAsrStop: (SpeakerDocumentUi) -> SpeakerPlaybackResult = { document ->
        if (speakerAsrState.isRecording) {
            speakerAsrController.stop()
        }
        playbackController.playDocument(document)
    }
    val playLineWithAsrStop: (SpeakerDocumentUi, Int, String) -> SpeakerPlaybackResult =
        { document, lineIndex, line ->
            if (speakerAsrState.isRecording) {
                speakerAsrController.stop()
            }
            playbackController.playLine(document, lineIndex, line)
        }
    val playDocumentFromAsr: (SpeakerDocumentUi) -> SpeakerPlaybackResult = { document ->
        playDocumentWithAsrStop(document)
    }
    val playLineFromAsr: (SpeakerDocumentUi, Int, String) -> SpeakerPlaybackResult =
        { document, lineIndex, line ->
            playLineWithAsrStop(document, lineIndex, line)
        }
    val resetContentSemanticUi: () -> Unit = speakerViewModel::resetContentSemanticUi
    val startContentAsrIfPossible: suspend () -> Unit = startContentAsr@{
        if (isAnyTtsPlaying) return@startContentAsr
        homeViewModel.ensureSelectedModelInitialized()
        activeAsrTarget = SpeakerAsrTarget.CONTENT
        speakerViewModel.updateContentAsrText("")
        speakerAsrController.start(userSettings)
    }

    LaunchedEffect(userSettings.userId) {
        speakerViewModel.setUserId(userSettings.userId)
    }

    LaunchedEffect(uiState.isImporting) {
        if (uiState.isImporting) {
            isImportProgressDialogVisible = true
        }
    }

    LaunchedEffect(uiState.isSyncing) {
        if (uiState.isSyncing) {
            isSyncProgressDialogVisible = true
        }
    }

    LaunchedEffect(speakerAsrState.partialText, speakerAsrState.finalText, activeAsrTarget) {
        val latestText = speakerAsrState.finalText.ifBlank { speakerAsrState.partialText }
        when (activeAsrTarget) {
            SpeakerAsrTarget.EXPLORER -> explorerAsrText = latestText
            SpeakerAsrTarget.CONTENT -> speakerViewModel.updateContentAsrText(latestText)
            null -> Unit
        }
    }

    LaunchedEffect(speakerAsrState.isRecording) {
        if (!speakerAsrState.isRecording) {
            activeAsrTarget = null
        }
    }

    LaunchedEffect(isExplorerWidgetVisible, isContentWidgetVisible, activeAsrTarget, speakerAsrState.isRecording) {
        val isTargetStillVisible = when (activeAsrTarget) {
            SpeakerAsrTarget.EXPLORER -> isExplorerWidgetVisible
            SpeakerAsrTarget.CONTENT -> isContentWidgetVisible
            null -> true
        }
        if (!isTargetStillVisible && speakerAsrState.isRecording) {
            speakerAsrController.stop()
        }
    }

    LaunchedEffect(isAnyTtsPlaying, speakerAsrState.isRecording) {
        if (isAnyTtsPlaying && speakerAsrState.isRecording) {
            speakerAsrController.stop()
        }
    }

    LaunchedEffect(selectedDocument?.id) {
        isContentSpeechModeEnabled = false
        speakerAsrController.stop()
        playbackController.stop()
        resetContentSemanticUi()
    }

    LaunchedEffect(selectedDocument?.id, expandedPane) {
        if (selectedDocument == null && expandedPane == SpeakerExpandedPane.CONTENT) {
            expandedPane = SpeakerExpandedPane.EXPLORER
        }
    }

    DisposableEffect(lifecycleOwner, playbackController, speakerAsrController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                isContentSpeechModeEnabled = false
                speakerAsrController.stop()
                playbackController.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            isContentSpeechModeEnabled = false
            speakerAsrController.stop()
            playbackController.stop()
        }
    }

    LaunchedEffect(
        playbackState.completionVersion,
        playbackState.completedDocumentId,
        selectedDocument?.id,
        isAnyTtsPlaying
    ) {
        if (!isContentSpeechModeEnabled) return@LaunchedEffect
        if (playbackState.completionVersion == 0) return@LaunchedEffect
        if (playbackState.completedDocumentId != selectedDocument?.id) {
            return@LaunchedEffect
        }
        if (selectedDocument == null || !isContentWidgetVisible || isAnyTtsPlaying || speakerAsrState.isRecording) {
            return@LaunchedEffect
        }
        startContentAsrIfPossible()
    }

    LaunchedEffect(
        speakerAsrState.finalTextVersion,
        activeAsrTarget,
        selectedDocument?.id,
        isSelectedDocumentPlaying,
        isSelectedDocumentPaused
    ) {
        if (activeAsrTarget != SpeakerAsrTarget.CONTENT) {
            Log.i(
                TAG,
                "Speaker content ASR final skipped because active target is $activeAsrTarget version=${speakerAsrState.finalTextVersion}"
            )
            return@LaunchedEffect
        }
        val utterance = speakerAsrState.finalUtteranceBundle ?: return@LaunchedEffect
        if (utterance.finalTextVersion == 0 || utterance.finalTextVersion == lastHandledContentFinalVersion) {
            Log.i(
                TAG,
                "Speaker content ASR final skipped because version=${utterance.finalTextVersion} lastHandled=$lastHandledContentFinalVersion"
            )
            return@LaunchedEffect
        }
        lastHandledContentFinalVersion = utterance.finalTextVersion

        val document = selectedDocument ?: return@LaunchedEffect
        val contentLines = buildSpeakerContentLines(document.fullText)
        Log.i(TAG, "Speaker content ASR text: ${utterance.primaryText}")
        Log.i(TAG, "Speaker content ASR variants: ${utterance.variants}")

        handleSpeakerContentAsr(
            context = context,
            semanticModule = semanticModule,
            utterance = utterance,
            document = document,
            contentLines = contentLines,
            indexedChunks = indexedSelectedDocumentChunks,
            isSelectedDocumentPlaying = isSelectedDocumentPlaying,
            isSelectedDocumentPaused = isSelectedDocumentPaused,
            isLlmFallbackEnabled = true,
            resetContentSemanticUi = resetContentSemanticUi,
            pauseDocument = playbackController::pause,
            stopPlayback = playbackController::stop,
            playDocumentWithAsrStop = playDocumentFromAsr,
            playLineWithAsrStop = playLineFromAsr,
            updateCandidateLineIndex = speakerViewModel::updateContentSemanticCandidateLineIndex,
            updateLlmFallbackState = speakerViewModel::updateLlmFallbackState
        )
    }

    fun toggleSpeakerAsr(target: SpeakerAsrTarget) {
        if (speakerAsrState.isRecording) {
            if (activeAsrTarget == target) {
                if (target == SpeakerAsrTarget.CONTENT) {
                    isContentSpeechModeEnabled = false
                }
                speakerAsrController.stop()
            }
            return
        }
        if (isAnyTtsPlaying) return
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
            return
        }
        scope.launch {
            homeViewModel.ensureSelectedModelInitialized()
            activeAsrTarget = target
            when (target) {
                SpeakerAsrTarget.EXPLORER -> explorerAsrText = ""
                SpeakerAsrTarget.CONTENT -> {
                    isContentSpeechModeEnabled = true
                    speakerViewModel.updateContentAsrText("")
                }
            }
            speakerAsrController.start(userSettings)
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

    fun showUploadSummaryToast(uploadedFolderCount: Int, uploadedDocumentCount: Int) {
        Toast.makeText(
            context,
            context.getString(
                R.string.speaker_cloud_upload_success,
                uploadedFolderCount,
                uploadedDocumentCount
            ),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun showFolderUploadSummaryToast(folderName: String, uploadedDocumentCount: Int) {
        Toast.makeText(
            context,
            context.getString(
                R.string.speaker_cloud_upload_folder_success,
                folderName,
                uploadedDocumentCount
            ),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun showCloudImportSummaryToast(importedFolderCount: Int, importedDocumentCount: Int) {
        Toast.makeText(
            context,
            context.getString(
                R.string.speaker_cloud_import_success,
                importedFolderCount,
                importedDocumentCount
            ),
            Toast.LENGTH_SHORT
        ).show()
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

        if (uiState.showRenameFolderDialog) {
            AlertDialog(
                onDismissRequest = { speakerViewModel.dismissRenameFolderDialog() },
                title = {
                    Text(text = stringResource(R.string.speaker_rename_folder))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.renameFolderName,
                            onValueChange = { speakerViewModel.onRenameFolderNameChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.speaker_folder_name_label)) },
                            placeholder = { Text(stringResource(R.string.speaker_folder_name_placeholder)) },
                            isError = uiState.renameFolderDialogError != null
                        )
                        if (uiState.renameFolderDialogError != null) {
                            Text(
                                text = uiState.renameFolderDialogError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { speakerViewModel.renameFolder() },
                        enabled = uiState.renameFolderName.isNotBlank()
                    ) {
                        Text(text = stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { speakerViewModel.dismissRenameFolderDialog() }
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (uiState.isImporting && isImportProgressDialogVisible) {
            AlertDialog(
                onDismissRequest = { isImportProgressDialogVisible = false },
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

        if (uiState.isSyncing && isSyncProgressDialogVisible) {
            AlertDialog(
                onDismissRequest = { isSyncProgressDialogVisible = false },
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.syncDirection == SpeakerSyncDirection.UPLOAD) {
                                R.string.speaker_cloud_upload_all
                            } else {
                                R.string.speaker_cloud_import
                            }
                        )
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (uiState.syncProgressTargetName.isBlank()) {
                                stringResource(R.string.speaker_sync_preparing)
                            } else {
                                stringResource(R.string.speaker_sync_target, uiState.syncProgressTargetName)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = if (uiState.syncProgressTotal == 0) 0f
                            else uiState.syncProgressCurrent.toFloat() / uiState.syncProgressTotal.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(
                                R.string.speaker_importing_progress,
                                uiState.syncProgressCurrent,
                                uiState.syncProgressTotal
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

        if (uiState.showCloudImportDialog) {
            AlertDialog(
                onDismissRequest = {
                    selectedRemoteFolderIds = emptySet()
                    speakerViewModel.dismissCloudImportDialog()
                },
                title = {
                    Text(text = stringResource(R.string.speaker_cloud_import))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.cloudSyncError != null) {
                            Text(
                                text = uiState.cloudSyncError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (uiState.remoteFolders.isEmpty()) {
                            Text(
                                text = stringResource(R.string.speaker_cloud_empty),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            uiState.remoteFolders.forEach { folder ->
                                RemoteFolderRow(
                                    folder = folder,
                                    isSelected = selectedRemoteFolderIds.contains(folder.id),
                                    onToggle = {
                                        selectedRemoteFolderIds = if (selectedRemoteFolderIds.contains(folder.id)) {
                                            selectedRemoteFolderIds - folder.id
                                        } else {
                                            selectedRemoteFolderIds + folder.id
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val targetFolders = uiState.remoteFolders.filter { selectedRemoteFolderIds.contains(it.id) }
                            if (targetFolders.isEmpty()) return@TextButton
                            scope.launch {
                                val result = speakerViewModel.importRemoteFolders(targetFolders)
                                result.onSuccess { summary ->
                                    selectedRemoteFolderIds = emptySet()
                                    showCloudImportSummaryToast(
                                        importedFolderCount = summary.importedFolders,
                                        importedDocumentCount = summary.importedDocuments
                                    )
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.speaker_cloud_import_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = selectedRemoteFolderIds.isNotEmpty() && !uiState.isSyncing
                    ) {
                        Text(text = stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            selectedRemoteFolderIds = emptySet()
                            speakerViewModel.dismissCloudImportDialog()
                        }
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (pendingCloudUploadDirectory != null) {
            AlertDialog(
                onDismissRequest = { pendingCloudUploadDirectory = null },
                title = {
                    Text(text = stringResource(R.string.speaker_cloud_upload))
                },
                text = {
                    Text(
                        text = stringResource(
                            R.string.speaker_cloud_upload_overwrite_confirm,
                            pendingCloudUploadDirectory.orEmpty()
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val folderName = pendingCloudUploadDirectory ?: return@TextButton
                            pendingCloudUploadDirectory = null
                            scope.launch {
                                val result = speakerViewModel.uploadFolderToCloud(folderName)
                                result.onSuccess { summary ->
                                    showFolderUploadSummaryToast(
                                        folderName = folderName,
                                        uploadedDocumentCount = summary.uploadedDocuments
                                    )
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.speaker_cloud_upload_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    ) {
                        Text(text = stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pendingCloudUploadDirectory = null }
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }

        SpeakerPaneHeader(
            title = stringResource(R.string.speaker_explorer_pane_title),
            isExpanded = expandedPane == SpeakerExpandedPane.EXPLORER,
            onClick = { expandedPane = SpeakerExpandedPane.EXPLORER }
        )

        if (expandedPane == SpeakerExpandedPane.EXPLORER) {
            SpeechFileExplorer(
                directories = uiState.directories,
                selectedDocumentId = uiState.selectedDocumentId,
                isLoading = uiState.isLoading,
                isImportEnabled = !uiState.isImporting && !uiState.isSyncing,
                isCloudSyncEnabled = !uiState.isImporting && !uiState.isSyncing,
                cloudStatusText = cloudStatusText,
                isDirectoryRenameEnabled = !uiState.isImporting &&
                    !uiState.isSyncing &&
                    !isSelectedDocumentPlaying &&
                    !isSelectedDocumentPaused,
                isDirectoryDeleteEnabled = !isSelectedDocumentPlaying && !isSelectedDocumentPaused,
                isDocumentDeleteEnabled = !isSelectedDocumentPlaying && !isSelectedDocumentPaused,
                onCreateFolder = { speakerViewModel.showCreateFolderDialog() },
                onFilePickerImport = { speakerViewModel.showDriveImportDialog() },
                onUploadDirectoryToCloud = { directory ->
                    pendingCloudUploadDirectory = directory.displayName
                },
                onRenameDirectory = { directory ->
                    speakerViewModel.showRenameFolderDialog(directory)
                },
                onCloudImport = {
                    selectedRemoteFolderIds = emptySet()
                    speakerViewModel.showCloudImportDialog()
                },
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
                    expandedPane = SpeakerExpandedPane.CONTENT
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        if (selectedDocument != null) {
            Spacer(modifier = Modifier.height(12.dp))

            SpeakerPaneHeader(
                title = stringResource(R.string.speaker_content_title),
                isExpanded = expandedPane == SpeakerExpandedPane.CONTENT,
                onClick = { expandedPane = SpeakerExpandedPane.CONTENT }
            )
        }

        if (selectedDocument != null && expandedPane == SpeakerExpandedPane.CONTENT) {
            SpeakerContentScreen(
                selectedDocument = selectedDocument,
                isTtsReady = playbackState.isReady,
                isPlaying = isSelectedDocumentPlaying,
                isPaused = isSelectedDocumentPaused,
                isEditing = uiState.isEditingDocument,
                localAsrText = uiState.contentAsrText,
                localAsrSecondaryText = uiState.llmFallbackState.toDisplayText(context),
                isLocalAsrRecording = activeAsrTarget == SpeakerAsrTarget.CONTENT && speakerAsrState.isRecording,
                localAsrCountdownProgress = if (activeAsrTarget == SpeakerAsrTarget.CONTENT && speakerAsrState.isRecognizingSpeech) speakerAsrState.countdownProgress else 0f,
                isLocalAsrEnabled = !isAnyTtsPlaying && !isAsrModelLoading && (!speakerAsrState.isRecording || activeAsrTarget == SpeakerAsrTarget.CONTENT),
                currentPlayingLineIndex = currentPlayingLineIndex,
                candidateLineIndex = uiState.contentSemanticCandidateLineIndex,
                editingText = uiState.editingText,
                onEditingTextChange = { speakerViewModel.onEditingTextChange(it) },
                onLocalAsrClick = { toggleSpeakerAsr(SpeakerAsrTarget.CONTENT) },
                onSpeakLine = { lineIndex, line ->
                    isContentSpeechModeEnabled = false
                    resetContentSemanticUi()
                    when (playLineWithAsrStop(selectedDocument, lineIndex, line)) {
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
                    isContentSpeechModeEnabled = false
                    resetContentSemanticUi()
                    when (playDocumentWithAsrStop(selectedDocument)) {
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
                    isContentSpeechModeEnabled = false
                    resetContentSemanticUi()
                    playbackController.pause(selectedDocument.id)
                },
                onStop = {
                    isContentSpeechModeEnabled = false
                    resetContentSemanticUi()
                    playbackController.stop()
                },
                onPreviousDocument = {
                    val targetDocument = previousDocument ?: return@SpeakerContentScreen
                    isContentSpeechModeEnabled = false
                    resetContentSemanticUi()
                    playbackController.stop()
                    speakerViewModel.onDocumentSelected(targetDocument.id)
                },
                onNextDocument = {
                    val targetDocument = nextDocument ?: return@SpeakerContentScreen
                    isContentSpeechModeEnabled = false
                    resetContentSemanticUi()
                    playbackController.stop()
                    speakerViewModel.onDocumentSelected(targetDocument.id)
                },
                isPreviousDocumentEnabled = previousDocument != null,
                isNextDocumentEnabled = nextDocument != null,
                onEdit = {
                    isContentSpeechModeEnabled = false
                    resetContentSemanticUi()
                    playbackController.stop()
                    speakerViewModel.startEditing()
                },
                onSave = {
                    isContentSpeechModeEnabled = false
                    resetContentSemanticUi()
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
                    resetContentSemanticUi()
                    speakerViewModel.cancelEditing()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun SpeakerPaneHeader(
    title: String,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun RemoteFolderRow(
    folder: SpeakerRemoteFolder,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = folder.folderName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.speaker_cloud_folder_count, folder.documentCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
