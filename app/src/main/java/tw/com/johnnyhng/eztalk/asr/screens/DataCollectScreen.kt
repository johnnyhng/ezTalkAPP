package tw.com.johnnyhng.eztalk.asr.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.SimulateStreamingAsr
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
    val selectedModel by homeViewModel.selectedModelFlow.collectAsState()
    val currentlyPlaying by MediaController.currentlyPlaying.collectAsState()
    val resultList = remember { mutableStateListOf<Transcript>() }
    val lazyColumnListState = rememberLazyListState()
    var isAsrModelLoading by remember { mutableStateOf(true) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    LaunchedEffect(selectedModel) {
        selectedModel?.let { model ->
            isAsrModelLoading = true
            withContext(Dispatchers.IO) {
                SimulateStreamingAsr.initOfflineRecognizer(context.assets, model)
            }
            isAsrModelLoading = false
        }
    }

    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.TRADITIONAL_CHINESE
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
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
        Text(
            text = stringResource(R.string.data_collect_page_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    if (!isStarted) {
                        if (uiState.text.isBlank()) {
                            Toast.makeText(context, R.string.text_empty_stopping_recording, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
                            return@Button
                        }
                    }
                    homeViewModel.toggleRecording(isDataCollectMode = true, dataCollectText = uiState.text)
                },
                enabled = !isAsrModelLoading
            ) {
                Text(text = stringResource(if (isStarted) R.string.stop else R.string.start))
            }
        }

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
                            tts?.speak(uiState.text, TextToSpeech.QUEUE_FLUSH, null, "data_collect_tts")
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
                    MediaController.play(path)
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
