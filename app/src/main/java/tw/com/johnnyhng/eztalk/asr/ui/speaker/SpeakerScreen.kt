package tw.com.johnnyhng.eztalk.asr.ui.speaker

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerSemanticDecision
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerSemanticModule
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerPlaybackResult
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerSearchResult
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerSemanticIndexer
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerViewModel
import tw.com.johnnyhng.eztalk.asr.speaker.importTextUrisIntoSpeakerFolder
import tw.com.johnnyhng.eztalk.asr.speaker.rememberSpeakerAsrController
import tw.com.johnnyhng.eztalk.asr.speaker.rememberSpeakerPlaybackController
import tw.com.johnnyhng.eztalk.asr.speaker.resolveSpeakerContentCommand
import tw.com.johnnyhng.eztalk.asr.speaker.sanitizeFolderName

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
    var contentAsrText by rememberSaveable { mutableStateOf("") }
    var lastHandledContentFinalVersion by rememberSaveable { mutableStateOf(0) }
    var expandedPane by rememberSaveable { mutableStateOf(SpeakerExpandedPane.EXPLORER) }
    var contentSemanticCandidateLineIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val semanticIndexer = remember { SpeakerSemanticIndexer() }
    val semanticModule = remember(appContext) {
        SpeakerSemanticModule(
            llmProvider = GeminiLlmProvider(
                accessTokenProvider = GoogleAuthGeminiAccessTokenProvider(appContext)
            )
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

    LaunchedEffect(userSettings.userId) {
        speakerViewModel.setUserId(userSettings.userId)
    }

    LaunchedEffect(speakerAsrState.partialText, speakerAsrState.finalText, activeAsrTarget) {
        val latestText = speakerAsrState.finalText.ifBlank { speakerAsrState.partialText }
        when (activeAsrTarget) {
            SpeakerAsrTarget.EXPLORER -> explorerAsrText = latestText
            SpeakerAsrTarget.CONTENT -> contentAsrText = latestText
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
        speakerAsrController.stop()
        playbackController.stop()
        contentAsrText = ""
        contentSemanticCandidateLineIndex = null
        speakerViewModel.updateLlmFallbackState(null)
    }

    LaunchedEffect(selectedDocument?.id, expandedPane) {
        if (selectedDocument == null && expandedPane == SpeakerExpandedPane.CONTENT) {
            expandedPane = SpeakerExpandedPane.EXPLORER
        }
    }

    DisposableEffect(lifecycleOwner, playbackController, speakerAsrController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                speakerAsrController.stop()
                playbackController.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            speakerAsrController.stop()
            playbackController.stop()
        }
    }

    LaunchedEffect(
        speakerAsrState.finalTextVersion,
        activeAsrTarget,
        selectedDocument?.id,
        isSelectedDocumentPlaying,
        isSelectedDocumentPaused
    ) {
        if (activeAsrTarget != SpeakerAsrTarget.CONTENT) return@LaunchedEffect
        if (speakerAsrState.finalTextVersion == 0 || speakerAsrState.finalTextVersion == lastHandledContentFinalVersion) {
            return@LaunchedEffect
        }
        lastHandledContentFinalVersion = speakerAsrState.finalTextVersion

        val document = selectedDocument ?: return@LaunchedEffect
        val contentLines = document.fullText.replace("\r\n", "\n").split('\n')
        val finalText = speakerAsrState.finalText
        Log.i(TAG, "Speaker content ASR text: $finalText")

        when (val command = resolveSpeakerContentCommand(finalText, contentLines)) {
            SpeakerContentCommand.Play -> {
                contentSemanticCandidateLineIndex = null
                Log.i(TAG, "Speaker content command matched: Play")
                when (playDocumentWithAsrStop(document)) {
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
            }

            SpeakerContentCommand.Pause -> {
                contentSemanticCandidateLineIndex = null
                Log.i(TAG, "Speaker content command matched: Pause")
                if (isSelectedDocumentPlaying) {
                    playbackController.pause(document.id)
                }
            }

            SpeakerContentCommand.Stop -> {
                contentSemanticCandidateLineIndex = null
                Log.i(TAG, "Speaker content command matched: Stop")
                if (isSelectedDocumentPlaying || isSelectedDocumentPaused) {
                    playbackController.stop()
                }
            }

            is SpeakerContentCommand.PlayLine -> {
                contentSemanticCandidateLineIndex = null
                Log.i(TAG, "Speaker content command matched: PlayLine(${command.lineIndex})")
                val lineText = contentLines.getOrNull(command.lineIndex).orEmpty()
                when (playLineWithAsrStop(document, command.lineIndex, lineText)) {
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
            }

            null -> {
                val resolution = semanticModule.resolve(
                    queryText = finalText,
                    lines = contentLines,
                    chunks = indexedSelectedDocumentChunks
                )
                Log.i(
                    TAG,
                    "Speaker semantic query embedding length=${resolution.query.embedding.size} preview=${resolution.query.embedding.previewForLog()}"
                )
                Log.i(
                    TAG,
                    "Speaker semantic top3 cosine=${resolution.rankedResults.take(3).formatTop3CosineForLog()}"
                )
                when (val decision = resolution.decision) {
                    SpeakerSemanticDecision.NoMatch -> {
                        contentSemanticCandidateLineIndex = null
                        Log.i(TAG, "Speaker semantic no matched content")
                        val llmRequest = semanticModule.buildLlmRequest(
                            queryText = finalText,
                            rankedResults = resolution.rankedResults
                        )
                        val llmFallbackResult = if (uiState.isLlmFallbackEnabled) {
                            semanticModule.tryLlmFallback(
                                queryText = finalText,
                                rankedResults = resolution.rankedResults,
                                lines = contentLines
                            )
                        } else {
                            null
                        }
                        speakerViewModel.updateLlmFallbackState(
                            when {
                                llmFallbackResult?.isSuccess == true -> SpeakerLlmFallbackState.Success(
                                    llmFallbackResult.getOrThrow()
                                )
                                llmFallbackResult?.isFailure == true -> SpeakerLlmFallbackState.Failure(
                                    llmFallbackResult.exceptionOrNull()?.message
                                        ?: context.getString(R.string.speaker_llm_preview_unavailable)
                                )
                                llmRequest != null -> SpeakerLlmFallbackState.PreviewReady(
                                    model = llmRequest.model,
                                    candidateCount = resolution.rankedResults.take(5).size
                                )
                                uiState.isLlmFallbackEnabled -> SpeakerLlmFallbackState.Unavailable
                                else -> null
                            }
                        )
                        val llmDecision = llmFallbackResult?.getOrNull()
                        when (llmDecision) {
                            is SpeakerSemanticDecision.Candidate -> {
                                contentSemanticCandidateLineIndex = llmDecision.lineIndex
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.speaker_semantic_candidate_selected,
                                        llmDecision.lineIndex + 1
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@LaunchedEffect
                            }

                            is SpeakerSemanticDecision.AutoPlay -> {
                                contentSemanticCandidateLineIndex = llmDecision.lineIndex
                                val lineText = contentLines.getOrNull(llmDecision.lineIndex).orEmpty()
                                when (playLineWithAsrStop(document, llmDecision.lineIndex, lineText)) {
                                    SpeakerPlaybackResult.NOT_READY -> {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.speaker_tts_not_ready),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    SpeakerPlaybackResult.EMPTY_TEXT -> Unit
                                    SpeakerPlaybackResult.STARTED -> {
                                        contentSemanticCandidateLineIndex = null
                                    }
                                }
                                return@LaunchedEffect
                            }

                            else -> Unit
                        }
                        Toast.makeText(
                            context,
                            context.getString(
                                when {
                                    llmFallbackResult?.isSuccess == true -> R.string.speaker_semantic_no_match_llm_applied
                                    llmRequest != null -> R.string.speaker_semantic_no_match_llm_preview
                                    else -> R.string.speaker_semantic_no_match
                                }
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is SpeakerSemanticDecision.Candidate -> {
                        contentSemanticCandidateLineIndex = decision.lineIndex
                        speakerViewModel.updateLlmFallbackState(null)
                        val result = decision.result
                        Log.i(
                            TAG,
                            "Speaker semantic candidate line=${decision.lineIndex} score=${"%.4f".format(result.finalScore)} semantic=${"%.4f".format(result.semanticScore)} lexical=${"%.4f".format(result.lexicalScore)} lines=${result.lineStart}-${result.lineEnd} text=${result.matchedText.oneLineForLog()}"
                        )
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.speaker_semantic_candidate_selected,
                                decision.lineIndex + 1
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is SpeakerSemanticDecision.AutoPlay -> {
                        val result = decision.result
                        speakerViewModel.updateLlmFallbackState(null)
                        Log.i(
                            TAG,
                            "Speaker semantic autoplay line=${decision.lineIndex} score=${"%.4f".format(result.finalScore)} semantic=${"%.4f".format(result.semanticScore)} lexical=${"%.4f".format(result.lexicalScore)} lines=${result.lineStart}-${result.lineEnd} text=${result.matchedText.oneLineForLog()}"
                        )
                        contentSemanticCandidateLineIndex = decision.lineIndex
                        val lineText = contentLines.getOrNull(decision.lineIndex).orEmpty()
                        when (playLineWithAsrStop(document, decision.lineIndex, lineText)) {
                            SpeakerPlaybackResult.NOT_READY -> {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.speaker_tts_not_ready),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            SpeakerPlaybackResult.EMPTY_TEXT -> Unit
                            SpeakerPlaybackResult.STARTED -> {
                                contentSemanticCandidateLineIndex = null
                            }
                        }
                    }

                    is SpeakerSemanticDecision.Ambiguous -> Unit
                }
            }
        }
    }

    fun toggleSpeakerAsr(target: SpeakerAsrTarget) {
        if (speakerAsrState.isRecording) {
            if (activeAsrTarget == target) {
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
                SpeakerAsrTarget.CONTENT -> contentAsrText = ""
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
                localAsrText = explorerAsrText,
                isLocalAsrRecording = activeAsrTarget == SpeakerAsrTarget.EXPLORER && speakerAsrState.isRecording,
                localAsrCountdownProgress = if (activeAsrTarget == SpeakerAsrTarget.EXPLORER && speakerAsrState.isRecognizingSpeech) speakerAsrState.countdownProgress else 0f,
                isLocalAsrEnabled = !isAnyTtsPlaying && !isAsrModelLoading && (!speakerAsrState.isRecording || activeAsrTarget == SpeakerAsrTarget.EXPLORER),
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
                localAsrText = contentAsrText,
                localAsrSecondaryText = uiState.llmFallbackState.toDisplayText(context),
                isLocalAsrRecording = activeAsrTarget == SpeakerAsrTarget.CONTENT && speakerAsrState.isRecording,
                localAsrCountdownProgress = if (activeAsrTarget == SpeakerAsrTarget.CONTENT && speakerAsrState.isRecognizingSpeech) speakerAsrState.countdownProgress else 0f,
                isLocalAsrEnabled = !isAnyTtsPlaying && !isAsrModelLoading && (!speakerAsrState.isRecording || activeAsrTarget == SpeakerAsrTarget.CONTENT),
                isLlmFallbackEnabled = uiState.isLlmFallbackEnabled,
                currentPlayingLineIndex = currentPlayingLineIndex,
                candidateLineIndex = contentSemanticCandidateLineIndex,
                editingText = uiState.editingText,
                onEditingTextChange = { speakerViewModel.onEditingTextChange(it) },
                onLlmFallbackToggle = speakerViewModel::onLlmFallbackEnabledChange,
                onLocalAsrClick = { toggleSpeakerAsr(SpeakerAsrTarget.CONTENT) },
                onSpeakLine = { lineIndex, line ->
                    contentSemanticCandidateLineIndex = null
                    speakerViewModel.updateLlmFallbackState(null)
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
                    contentSemanticCandidateLineIndex = null
                    speakerViewModel.updateLlmFallbackState(null)
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
                    contentSemanticCandidateLineIndex = null
                    speakerViewModel.updateLlmFallbackState(null)
                    playbackController.pause(selectedDocument.id)
                },
                onStop = {
                    contentSemanticCandidateLineIndex = null
                    speakerViewModel.updateLlmFallbackState(null)
                    playbackController.stop()
                },
                onPreviousDocument = {
                    val targetDocument = previousDocument ?: return@SpeakerContentScreen
                    contentSemanticCandidateLineIndex = null
                    speakerViewModel.updateLlmFallbackState(null)
                    playbackController.stop()
                    speakerViewModel.onDocumentSelected(targetDocument.id)
                },
                onNextDocument = {
                    val targetDocument = nextDocument ?: return@SpeakerContentScreen
                    contentSemanticCandidateLineIndex = null
                    speakerViewModel.updateLlmFallbackState(null)
                    playbackController.stop()
                    speakerViewModel.onDocumentSelected(targetDocument.id)
                },
                isPreviousDocumentEnabled = previousDocument != null,
                isNextDocumentEnabled = nextDocument != null,
                onEdit = {
                    contentSemanticCandidateLineIndex = null
                    speakerViewModel.updateLlmFallbackState(null)
                    playbackController.stop()
                    speakerViewModel.startEditing()
                },
                onSave = {
                    contentSemanticCandidateLineIndex = null
                    speakerViewModel.updateLlmFallbackState(null)
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
                    contentSemanticCandidateLineIndex = null
                    speakerViewModel.updateLlmFallbackState(null)
                    speakerViewModel.cancelEditing()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

private fun FloatArray.previewForLog(maxSize: Int = 8): String {
    if (isEmpty()) return "[]"
    return take(maxSize).joinToString(
        prefix = "[",
        postfix = if (size > maxSize) ", ...]" else "]"
    ) { value ->
        "%.4f".format(value)
    }
}

private fun SpeakerLlmFallbackState?.toDisplayText(context: android.content.Context): String? {
    return when (this) {
        is SpeakerLlmFallbackState.PreviewReady -> context.getString(
            R.string.speaker_llm_preview_status,
            model,
            candidateCount
        )
        is SpeakerLlmFallbackState.Success -> context.getString(
            R.string.speaker_llm_fallback_success,
            decision.javaClass.simpleName
        )
        is SpeakerLlmFallbackState.Failure -> context.getString(
            R.string.speaker_llm_fallback_failed,
            message
        )
        SpeakerLlmFallbackState.Unavailable -> context.getString(R.string.speaker_llm_preview_unavailable)
        null -> null
    }
}

private fun List<SpeakerSearchResult>.formatTop3CosineForLog(): String {
    if (isEmpty()) return "[]"
    return take(3).joinToString(
        prefix = "[",
        postfix = "]"
    ) { result ->
        "{cos=${"%.4f".format(result.semanticScore)}, hybrid=${"%.4f".format(result.finalScore)}, lines=${result.lineStart}-${result.lineEnd}, text=${result.matchedText.oneLineForLog()}}"
    }
}

private fun String.oneLineForLog(maxLength: Int = 60): String {
    val normalized = replace('\n', ' ').trim()
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        normalized.take(maxLength) + "..."
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
