package tw.com.johnnyhng.eztalk.asr.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.SimulateStreamingAsr
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.data.classes.QueueState
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.utils.*
import tw.com.johnnyhng.eztalk.asr.widgets.*
import java.io.File
import java.util.*

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context as Activity
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // UI state tied to ViewModel
    val isStarted by homeViewModel.isRecording.collectAsState()
    val latestAudioSamples by homeViewModel.latestSamples.collectAsState()
    val isRecognizingSpeech by homeViewModel.isRecognizingSpeech.collectAsState()
    val countdownProgress by homeViewModel.countdownProgress.collectAsState()
    val userSettings by homeViewModel.userSettings.collectAsState()
    val selectedModel by homeViewModel.selectedModelFlow.collectAsState()
    
    val resultList = remember { mutableStateListOf<Transcript>() }
    val lazyColumnListState = rememberLazyListState()

    // Local UI states
    var isInlineEditing by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var editingText by remember { mutableStateOf("") }
    var localCandidate by remember { mutableStateOf<String?>(null) }
    var isFetchingCandidates by remember { mutableStateOf(false) }
    var transcriptToEditInDialog by remember { mutableStateOf<Pair<Int, Transcript>?>(null) }
    val isEditing = isInlineEditing || transcriptToEditInDialog != null
    val fetchingJobs = remember { mutableStateMapOf<String, Job>() }

    // Data collect states
    var isDataCollectMode by remember { mutableStateOf(false) }
    var dataCollectText by remember { mutableStateOf("") }
    var isSequenceMode by rememberSaveable { mutableStateOf(false) }
    val textQueue = rememberSaveable(saver = listSaver(
        save = { it.toList() },
        restore = { it.toMutableStateList() }
    )) { mutableStateListOf<String>() }
    var showNoQueueMessage by remember { mutableStateOf(false) }

    // TTS and Background Logic
    val currentlyPlaying by MediaController.currentlyPlaying.collectAsState()
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsSpeaking by remember { mutableStateOf(false) }
    val recognitionQueue = remember { Channel<String>(Channel.UNLIMITED) }

    // Model Loading
    var isAsrModelLoading by remember { mutableStateOf(true) }
    LaunchedEffect(selectedModel) {
        selectedModel?.let { model ->
            isAsrModelLoading = true
            withContext(Dispatchers.IO) {
                SimulateStreamingAsr.initOfflineRecognizer(context.assets, model)
            }
            isAsrModelLoading = false
        }
    }

    // Background recognition queue processor
    LaunchedEffect(userSettings.effectiveRecognitionUrl, userSettings.userId) {
        for (wavPath in recognitionQueue) {
            val url = userSettings.effectiveRecognitionUrl
            if (url.isBlank() || isDataCollectMode) continue

            coroutineScope.launch(Dispatchers.IO) {
                val transcript = resultList.find { it.wavFilePath == wavPath }
                transcript?.let {
                    val sentences = getRemoteCandidates(
                        context = context,
                        wavFilePath = wavPath,
                        userId = userSettings.userId,
                        recognitionUrl = url,
                        originalText = it.recognizedText,
                        currentText = it.modifiedText
                    )

                    if (sentences.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val index = resultList.indexOfFirst { r -> r.wavFilePath == wavPath }
                            if (index != -1) {
                                resultList[index] = resultList[index].copy(remoteCandidates = sentences)
                            }
                        }
                    }
                }
            }
        }
    }

    // Handlers
    fun handleTtsClick(index: Int, text: String) {
        MediaController.stop()
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        
        val item = resultList[index]
        if (userSettings.enableTtsFeedback && !isDataCollectMode) {
            coroutineScope.launch {
                fetchingJobs[item.wavFilePath]?.join()
                if (item.removable) return@launch
                val success = withContext(Dispatchers.IO) {
                    feedbackToBackend(userSettings.backendUrl, item.wavFilePath, userSettings.userId)
                }
                if (success) {
                    withContext(Dispatchers.Main) {
                        val cIndex = resultList.indexOfFirst { it.wavFilePath == item.wavFilePath }
                        if (cIndex != -1) {
                            val updated = resultList[cIndex].copy(modifiedText = text, checked = true, mutable = false, removable = true)
                            resultList[cIndex] = updated
                            withContext(Dispatchers.IO) {
                                saveJsonl(context, userSettings.userId, File(updated.wavFilePath).nameWithoutExtension, updated.recognizedText, text, true, false, true)
                            }
                        }
                    }
                }
            }
        } else {
            val updated = item.copy(modifiedText = text, checked = true)
            resultList[index] = updated
            coroutineScope.launch(Dispatchers.IO) {
                saveJsonl(context, userSettings.userId, File(updated.wavFilePath).nameWithoutExtension, updated.recognizedText, text, true, updated.mutable)
            }
        }
    }

    // Connect ViewModel events to resultList
    LaunchedEffect(Unit) {
        homeViewModel.finalTranscript.collect { transcript ->
            if (transcript.modifiedText.isNotBlank()) {
                if (resultList.isNotEmpty() && resultList.last().wavFilePath.isEmpty()) {
                    resultList[resultList.size - 1] = transcript
                } else {
                    resultList.add(transcript)
                }
                lazyColumnListState.animateScrollToItem(resultList.size - 1)
                
                if (userSettings.effectiveRecognitionUrl.isNotBlank() && !isDataCollectMode) {
                    recognitionQueue.trySend(transcript.wavFilePath)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.partialText.collect { text ->
            if (resultList.isEmpty() || resultList.last().wavFilePath.isNotEmpty()) {
                resultList.add(Transcript(recognizedText = text, modifiedText = text, wavFilePath = ""))
            } else {
                resultList[resultList.size - 1] = resultList.last().copy(recognizedText = text, modifiedText = text)
            }
            lazyColumnListState.animateScrollToItem(resultList.size - 1)
        }
    }

    // TTS Init
    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.TRADITIONAL_CHINESE
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { isTtsSpeaking = true }
            override fun onDone(id: String?) { isTtsSpeaking = false }
            override fun onError(id: String?) { isTtsSpeaking = false }
        })
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
            recognitionQueue.close()
        }
    }

    LaunchedEffect(userSettings.inlineEdit) {
        if (!userSettings.inlineEdit && isInlineEditing) {
            isInlineEditing = false
            editingIndex = -1
            isFetchingCandidates = false
            localCandidate = null
        }
    }

    // File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val lines = stream.bufferedReader().readLines().filter { it.isNotBlank() }
                    withContext(Dispatchers.Main) {
                        textQueue.clear()
                        if (lines.isNotEmpty()) {
                            dataCollectText = lines.first()
                            textQueue.addAll(lines.drop(1))
                            isSequenceMode = true
                            saveQueueState(context, userSettings.userId, QueueState(dataCollectText, textQueue.toList()))
                        }
                    }
                }
            }
        }
    }

    // Main UI
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text(stringResource(R.string.data_collect_mode))
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = isDataCollectMode, onCheckedChange = { isDataCollectMode = it })
            }

            if (isDataCollectMode) {
                DataCollectWidget(
                    text = dataCollectText,
                    onTextChange = { dataCollectText = it },
                    onTtsClick = { if (dataCollectText.isNotBlank()) tts?.speak(dataCollectText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString()) },
                    isSequenceMode = isSequenceMode,
                    onSequenceModeChange = { newMode ->
                        if (newMode) {
                            restoreQueueState(context, userSettings.userId)?.let {
                                textQueue.clear()
                                textQueue.addAll(it.queue)
                                dataCollectText = it.currentText
                                isSequenceMode = true
                            } ?: run { showNoQueueMessage = true }
                        } else {
                            isSequenceMode = false
                        }
                    },
                    onUploadClick = { filePickerLauncher.launch("text/plain") },
                    onNextClick = {
                        if (textQueue.isNotEmpty()) {
                            dataCollectText = textQueue.removeFirst()
                            saveQueueState(context, userSettings.userId, QueueState(dataCollectText, textQueue.toList()))
                        } else {
                            isSequenceMode = false
                            deleteQueueState(context, userSettings.userId)
                        }
                    },
                    onDeleteClick = { dataCollectText = "" },
                    isNextEnabled = isSequenceMode,
                    isSequenceModeSwitchEnabled = true,
                    showNoQueueMessage = showNoQueueMessage
                )
            }

            HomeButtonRow(
                isStarted = isStarted,
                onRecordingButtonClick = {
                    if (!isStarted) {
                        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
                            return@HomeButtonRow
                        }
                    }
                    homeViewModel.toggleRecording(isDataCollectMode, dataCollectText) 
                },
                onCopyButtonClick = {
                    if (resultList.isNotEmpty()) {
                        val s = resultList.mapIndexed { i, r -> "${i + 1}: ${r.modifiedText}" }.joinToString("")
                        clipboardManager.setText(AnnotatedString(s))
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                    }
                },
                onClearButtonClick = { resultList.clear() },
                isPlaybackActive = currentlyPlaying != null || isTtsSpeaking,
                isAsrModelLoading = isAsrModelLoading,
                isEditing = isEditing,
                isDataCollectMode = isDataCollectMode,
                dataCollectText = dataCollectText
            )

            if (isStarted) {
                WaveformDisplay(samples = latestAudioSamples, modifier = Modifier.fillMaxWidth().height(100.dp).padding(vertical = 8.dp))
            }

            CandidateList(
                resultList = resultList,
                lazyListState = lazyColumnListState,
                isInteractionLocked = isEditing,
                isInlineEditing = isInlineEditing,
                editingIndex = editingIndex,
                editingText = editingText,
                onEditingTextChange = { editingText = it },
                onCancelEdit = { isInlineEditing = false; editingIndex = -1 },
                onConfirmEdit = { idx, txt -> handleTtsClick(idx, txt); isInlineEditing = false; editingIndex = -1 },
                onItemClick = { idx, res -> 
                    if (userSettings.inlineEdit) {
                        isInlineEditing = true
                        editingIndex = idx
                        editingText = res.modifiedText
                        
                        if (res.wavFilePath.isNotEmpty()) {
                            coroutineScope.launch {
                                isFetchingCandidates = true
                                val localJob = launch(Dispatchers.IO) {
                                    try {
                                        val audioData = readWavFileToFloatArray(res.wavFilePath)
                                        if (audioData != null) {
                                            val recognizer = SimulateStreamingAsr.recognizer
                                            val stream = recognizer.createStream()
                                            stream.acceptWaveform(audioData, 16000)
                                            recognizer.decode(stream)
                                            val localResultText = recognizer.getResult(stream).text
                                            stream.release()
                                            withContext(Dispatchers.Main) { localCandidate = localResultText }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Local re-recognition failed", e)
                                    }
                                }
                                localJob.join()
                                isFetchingCandidates = false
                            }
                        }
                    } else {
                        isInlineEditing = false
                        editingIndex = -1
                        isFetchingCandidates = false
                        localCandidate = null
                        transcriptToEditInDialog = idx to res
                    }
                },
                onTtsClick = { idx, txt -> handleTtsClick(idx, txt) },
                onPlayClick = { path -> if (currentlyPlaying == path) MediaController.stop() else MediaController.play(path) },
                onDeleteClick = { idx, path -> if (path.isNotEmpty() && deleteTranscriptFiles(path)) resultList.removeAt(idx) },
                isRecognizingSpeech = isRecognizingSpeech,
                currentlyPlaying = currentlyPlaying,
                isStarted = isStarted,
                isTtsSpeaking = isTtsSpeaking,
                countdownProgress = countdownProgress,
                isDataCollectMode = isDataCollectMode,
                inlineEditEnabled = userSettings.inlineEdit,
                localCandidate = localCandidate,
                isFetchingCandidates = isFetchingCandidates
            )
        }

        transcriptToEditInDialog?.let { (index, transcript) ->
            EditRecognitionDialog(
                originalText = transcript.recognizedText,
                currentText = transcript.modifiedText,
                wavFilePath = transcript.wavFilePath,
                onDismiss = {
                    transcriptToEditInDialog = null
                },
                onConfirm = { newText ->
                    val updatedItem = transcript.copy(modifiedText = newText, checked = true)
                    resultList[index] = updatedItem
                    coroutineScope.launch(Dispatchers.IO) {
                        saveJsonl(context, userSettings.userId, File(updatedItem.wavFilePath).nameWithoutExtension, updatedItem.recognizedText, newText, true, updatedItem.mutable)
                    }
                    transcriptToEditInDialog = null
                },
                userId = userSettings.userId,
                recognitionUrl = userSettings.effectiveRecognitionUrl,
            )
        }
    }
}

@Composable
private fun HomeButtonRow(
    isStarted: Boolean,
    onRecordingButtonClick: () -> Unit,
    onCopyButtonClick: () -> Unit,
    onClearButtonClick: () -> Unit,
    isPlaybackActive: Boolean,
    isAsrModelLoading: Boolean,
    isEditing: Boolean,
    isDataCollectMode: Boolean,
    dataCollectText: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Button(onClick = onRecordingButtonClick, enabled = !isPlaybackActive && !isAsrModelLoading && !isEditing && (!isDataCollectMode || dataCollectText.isNotBlank())) {
            Text(text = stringResource(if (isStarted) R.string.stop else R.string.start))
        }
        Spacer(modifier = Modifier.width(24.dp))
        Button(onClick = onCopyButtonClick, enabled = !isStarted && !isPlaybackActive && !isEditing) {
            Text(text = stringResource(id = R.string.copy))
        }
        Spacer(modifier = Modifier.width(24.dp))
        Button(onClick = onClearButtonClick, enabled = !isStarted && !isPlaybackActive && !isEditing) {
            Text(text = stringResource(id = R.string.clear))
        }
    }
}
