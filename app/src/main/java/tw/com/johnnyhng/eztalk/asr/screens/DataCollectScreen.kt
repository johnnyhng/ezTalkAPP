package tw.com.johnnyhng.eztalk.asr.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.com.johnnyhng.eztalk.asr.audio.rememberSpeechOutputController
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.managers.DataCollectViewModel
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.utils.MediaController
import tw.com.johnnyhng.eztalk.asr.utils.deleteTranscriptFiles
import tw.com.johnnyhng.eztalk.asr.widgets.CandidateList
import tw.com.johnnyhng.eztalk.asr.widgets.WaveformDisplay
import java.util.Locale
import tw.com.johnnyhng.eztalk.asr.R

@Composable
fun DataCollectScreen(
    dataCollectViewModel: DataCollectViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context as Activity
    val uiState by dataCollectViewModel.uiState.collectAsState()
    val isStarted by homeViewModel.isRecording.collectAsState()
    val latestAudioSamples by homeViewModel.latestSamples.collectAsState()
    val isRecognizingSpeech by homeViewModel.isRecognizingSpeech.collectAsState()
    val countdownProgress by homeViewModel.countdownProgress.collectAsState()
    val userSettings by homeViewModel.userSettings.collectAsState()
    val selectedModel by homeViewModel.selectedModelFlow.collectAsState()
    val isAsrModelLoading by homeViewModel.isAsrModelLoading.collectAsState()
    val currentlyPlaying by MediaController.currentlyPlaying.collectAsState()
    val resultList = remember { mutableStateListOf<Transcript>() }
    val lazyColumnListState = rememberLazyListState()
    val (speechController, _) = rememberSpeechOutputController(
        preferredLocale = Locale.TRADITIONAL_CHINESE,
        preferredOutputDeviceId = userSettings.preferredAudioOutputDeviceId
    )
    var isAutoFlowEnabled by rememberSaveable { mutableStateOf(false) }
    val latestIsSequenceMode by rememberUpdatedState(uiState.isSequenceMode)
    val latestIsAutoFlowEnabled by rememberUpdatedState(isAutoFlowEnabled)

    LaunchedEffect(selectedModel?.name, userSettings.userId, userSettings.mobileModelSha256) {
        if (selectedModel != null) {
            homeViewModel.ensureSelectedModelInitialized()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechController.stop()
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.partialText.collect { text ->
            if (resultList.isEmpty() || resultList.last().wavFilePath.isNotEmpty()) {
                resultList.add(Transcript(recognizedText = text, modifiedText = text, wavFilePath = ""))
            } else {
                resultList[resultList.lastIndex] = resultList.last().copy(recognizedText = text, modifiedText = text)
            }
            lazyColumnListState.animateScrollToItem(resultList.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.finalTranscript.collect { transcript ->
            if (transcript.modifiedText.isNotBlank()) {
                if (resultList.isNotEmpty() && resultList.last().wavFilePath.isEmpty()) {
                    resultList[resultList.lastIndex] = transcript
                } else {
                    resultList.add(transcript)
                }
                lazyColumnListState.animateScrollToItem(resultList.lastIndex)

                if (latestIsSequenceMode && latestIsAutoFlowEnabled && transcript.wavFilePath.isNotEmpty()) {
                    dataCollectViewModel.moveToNext()
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        dataCollectViewModel.importFromUri(uri)
    }

    LaunchedEffect(uiState.text) {
        homeViewModel.updateDataCollectText(uiState.text)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isStarted) {
            WaveformDisplay(
                samples = latestAudioSamples,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                DataCollectWidget(
                    text = uiState.text,
                    onTextChange = dataCollectViewModel::onTextChange,
                    onTtsClick = {
                        if (uiState.text.isNotBlank()) {
                            speechController.speak(uiState.text)
                        }
                    },
                    isSequenceMode = uiState.isSequenceMode,
                    onSequenceModeChange = dataCollectViewModel::onSequenceModeChange,
                    onUploadClick = { filePickerLauncher.launch("text/*") },
                    onPreviousClick = dataCollectViewModel::moveToPrevious,
                    onNextClick = dataCollectViewModel::moveToNext,
                    onDeleteClick = dataCollectViewModel::clearText,
                    isPreviousEnabled = uiState.previousCount > 0,
                    isNextEnabled = uiState.isSequenceMode,
                    isSequenceModeSwitchEnabled = true,
                    showNoQueueMessage = uiState.showNoQueueMessage
                )
            }
        }

        Text(
            text = stringResource(
                R.string.data_collect_queue_status,
                uiState.previousCount,
                uiState.remainingCount
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.data_collect_auto_flow),
                style = MaterialTheme.typography.bodyMedium
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isAutoFlowEnabled,
                onCheckedChange = { isAutoFlowEnabled = it }
            )
        }

        DataCollectButtonRow(
            isStarted = isStarted,
            isAsrModelLoading = isAsrModelLoading,
            canStart = uiState.text.isNotBlank(),
            canSkip = uiState.isSequenceMode,
            canRetry = uiState.previousCount > 0 && resultList.any { it.wavFilePath.isNotEmpty() },
            onRecordingButtonClick = {
                if (!isStarted) {
                    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
                    } else {
                        homeViewModel.startDataCollectRecording(uiState.text)
                    }
                } else {
                    homeViewModel.toggleRecording()
                }
            },
            onSkipClick = dataCollectViewModel::skipCurrent,
            onRetryClick = {
                val lastCompletedIndex = resultList.indexOfLast { it.wavFilePath.isNotEmpty() }
                val canRollback = if (lastCompletedIndex != -1) {
                    val lastCompleted = resultList[lastCompletedIndex]
                    if (deleteTranscriptFiles(lastCompleted.wavFilePath)) {
                        resultList.removeAt(lastCompletedIndex)
                        true
                    } else {
                        false
                    }
                } else {
                    true
                }
                if (canRollback) {
                    dataCollectViewModel.retryLastCompleted()
                }
            }
        )

        CandidateList(
            modifier = Modifier.fillMaxWidth(),
            resultList = resultList,
            lazyListState = lazyColumnListState,
            isInteractionLocked = false,
            isInlineEditing = false,
            editingIndex = -1,
            editingText = "",
            onEditingTextChange = {},
            onCancelEdit = {},
            onConfirmEdit = { _, _ -> },
            onItemClick = { _, _ -> },
            onTtsClick = { _, _ -> },
            onPlayClick = { path ->
                if (currentlyPlaying == path) {
                    MediaController.stop()
                } else {
                    MediaController.play(
                        context = context,
                        filePath = path,
                        userSettings = userSettings,
                        onRoutingApplied = homeViewModel::reportAudioRoutingMessage
                    )
                }
            },
            onDeleteClick = { idx, path ->
                if (path.isNotEmpty() && deleteTranscriptFiles(path)) {
                    resultList.removeAt(idx)
                }
            },
            isRecognizingSpeech = isRecognizingSpeech,
            currentlyPlaying = currentlyPlaying,
            isStarted = isStarted,
            isTtsSpeaking = false,
            countdownProgress = countdownProgress,
            isDataCollectMode = true,
            inlineEditEnabled = false,
            localCandidate = null,
            isFetchingCandidates = false
        )
    }
}

@Composable
private fun DataCollectButtonRow(
    isStarted: Boolean,
    isAsrModelLoading: Boolean,
    canStart: Boolean,
    canSkip: Boolean,
    canRetry: Boolean,
    onRecordingButtonClick: () -> Unit,
    onSkipClick: () -> Unit,
    onRetryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onRecordingButtonClick,
            enabled = !isAsrModelLoading && (isStarted || canStart)
        ) {
            Text(text = stringResource(if (isStarted) R.string.stop else R.string.start))
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(24.dp))
        Button(
            onClick = onRetryClick,
            enabled = !isStarted && canRetry
        ) {
            Text(text = stringResource(R.string.retry))
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(24.dp))
        Button(
            onClick = onSkipClick,
            enabled = !isStarted && canSkip
        ) {
            Text(text = stringResource(R.string.skip))
        }
    }
}
