package tw.com.johnnyhng.eztalk.asr.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.SimulateStreamingAsr
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
    val isAsrModelLoading by homeViewModel.isAsrModelLoading.collectAsState()
    
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

    // TTS and Background Logic
    val currentlyPlaying by MediaController.currentlyPlaying.collectAsState()
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsSpeaking by remember { mutableStateOf(false) }
    val recognitionQueue = remember { Channel<String>(Channel.UNLIMITED) }

    LaunchedEffect(selectedModel) {
        if (selectedModel != null) {
            homeViewModel.ensureSelectedModelInitialized()
        }
    }

    // Background recognition queue processor
    LaunchedEffect(userSettings.effectiveRecognitionUrl, userSettings.userId) {
        for (wavPath in recognitionQueue) {
            val url = userSettings.effectiveRecognitionUrl
            if (url.isBlank()) continue

            val job = coroutineScope.launch(Dispatchers.IO) {
                val transcript = withContext(Dispatchers.Main) {
                    resultList.find { it.wavFilePath == wavPath }
                }

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
                } ?: Log.w(TAG, "Could not find transcript for wavPath: $wavPath to update remote candidates.")
            }

            fetchingJobs[wavPath] = job
            job.invokeOnCompletion {
                fetchingJobs.remove(wavPath)
            }
        }
    }

    // Handlers
    fun handleTtsClick(index: Int, text: String) {
        MediaController.stop()
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        
        val item = resultList[index]
        val useFeedbackLogic = userSettings.enableTtsFeedback
        if (useFeedbackLogic) {
            coroutineScope.launch {
                fetchingJobs[item.wavFilePath]?.join()
                if (item.removable) return@launch
                val success = withContext(Dispatchers.IO) {
                    feedbackToBackend(userSettings.backendUrl, item.wavFilePath, userSettings.userId)
                }
                if (success) {
                    val updated = withContext(Dispatchers.Main) {
                        val cIndex = resultList.indexOfFirst { it.wavFilePath == item.wavFilePath }
                        if (cIndex == -1) {
                            null
                        } else {
                            resultList[cIndex].copy(
                                modifiedText = text,
                                checked = true,
                                mutable = false,
                                removable = true
                            ).also { resultList[cIndex] = it }
                        }
                    }

                    updated?.let {
                        withContext(Dispatchers.IO) {
                            saveJsonl(
                                context = context,
                                userId = userSettings.userId,
                                filename = File(it.wavFilePath).nameWithoutExtension,
                                originalText = it.recognizedText,
                                modifiedText = it.modifiedText,
                                checked = it.checked,
                                mutable = it.mutable,
                                removable = it.removable,
                                localCandidates = it.localCandidates,
                                remoteCandidates = it.remoteCandidates
                            )
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.feedback_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            val updated = item.copy(modifiedText = text, checked = true)
            resultList[index] = updated
            coroutineScope.launch(Dispatchers.IO) {
                saveJsonl(
                    context = context,
                    userId = userSettings.userId,
                    filename = File(updated.wavFilePath).nameWithoutExtension,
                    originalText = updated.recognizedText,
                    modifiedText = updated.modifiedText,
                    checked = updated.checked,
                    mutable = updated.mutable,
                    removable = updated.removable,
                    localCandidates = updated.localCandidates,
                    remoteCandidates = updated.remoteCandidates
                )
            }
        }
    }

    fun handleDialogTtsConfirm(index: Int, text: String) {
        val item = resultList.getOrNull(index) ?: return

        if (userSettings.enableTtsFeedback) {
            coroutineScope.launch {
                fetchingJobs[item.wavFilePath]?.join()
                if (item.removable) {
                    val updatedItem = item.copy(modifiedText = text, checked = true, mutable = false, removable = true)
                    resultList[index] = updatedItem
                    withContext(Dispatchers.IO) {
                        saveJsonl(
                            context = context,
                            userId = userSettings.userId,
                            filename = File(updatedItem.wavFilePath).nameWithoutExtension,
                            originalText = updatedItem.recognizedText,
                            modifiedText = updatedItem.modifiedText,
                            checked = updatedItem.checked,
                            mutable = updatedItem.mutable,
                            removable = updatedItem.removable,
                            localCandidates = updatedItem.localCandidates,
                            remoteCandidates = updatedItem.remoteCandidates
                        )
                    }
                    transcriptToEditInDialog = null
                    return@launch
                }

                val success = withContext(Dispatchers.IO) {
                    feedbackToBackend(userSettings.backendUrl, item.wavFilePath, userSettings.userId)
                }

                if (success) {
                    val updatedItem = item.copy(
                        modifiedText = text,
                        checked = true,
                        mutable = false,
                        removable = true
                    )
                    resultList[index] = updatedItem
                    withContext(Dispatchers.IO) {
                        saveJsonl(
                            context = context,
                            userId = userSettings.userId,
                            filename = File(updatedItem.wavFilePath).nameWithoutExtension,
                            originalText = updatedItem.recognizedText,
                            modifiedText = updatedItem.modifiedText,
                            checked = updatedItem.checked,
                            mutable = updatedItem.mutable,
                            removable = updatedItem.removable,
                            localCandidates = updatedItem.localCandidates,
                            remoteCandidates = updatedItem.remoteCandidates
                        )
                    }
                    transcriptToEditInDialog = null
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.feedback_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            val updatedItem = item.copy(modifiedText = text, checked = true)
            resultList[index] = updatedItem
            coroutineScope.launch(Dispatchers.IO) {
                saveJsonl(
                    context = context,
                    userId = userSettings.userId,
                    filename = File(updatedItem.wavFilePath).nameWithoutExtension,
                    originalText = updatedItem.recognizedText,
                    modifiedText = updatedItem.modifiedText,
                    checked = updatedItem.checked,
                    mutable = updatedItem.mutable,
                    removable = updatedItem.removable,
                    localCandidates = updatedItem.localCandidates,
                    remoteCandidates = updatedItem.remoteCandidates
                )
            }
            transcriptToEditInDialog = null
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
                
                if (userSettings.effectiveRecognitionUrl.isNotBlank()) {
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
            if (status == TextToSpeech.SUCCESS) {
                if (tts?.isLanguageAvailable(Locale.TRADITIONAL_CHINESE) == TextToSpeech.LANG_AVAILABLE) {
                    tts?.language = Locale.TRADITIONAL_CHINESE
                } else {
                    tts?.language = Locale.getDefault()
                }
            }
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

    // Main UI
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column {
            HomeButtonRow(
                isStarted = isStarted,
                onRecordingButtonClick = {
                    if (!isStarted) {
                        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
                            return@HomeButtonRow
                        }
                        homeViewModel.startTranslateRecording()
                    } else {
                        homeViewModel.toggleRecording()
                    }
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
                isEditing = isEditing
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
                                if (res.localCandidates.isNotEmpty()) {
                                    localCandidate = res.localCandidates.firstOrNull()
                                } else {
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
                                                if (localResultText.isNotBlank()) {
                                                    val updatedItem = res.copy(localCandidates = listOf(localResultText))
                                                    withContext(Dispatchers.Main) {
                                                        localCandidate = localResultText
                                                        if (idx in resultList.indices && resultList[idx].wavFilePath == res.wavFilePath) {
                                                            resultList[idx] = updatedItem
                                                        }
                                                    }
                                                    saveJsonl(
                                                        context = context,
                                                        userId = userSettings.userId,
                                                        filename = File(res.wavFilePath).nameWithoutExtension,
                                                        originalText = updatedItem.recognizedText,
                                                        modifiedText = updatedItem.modifiedText,
                                                        checked = updatedItem.checked,
                                                        mutable = updatedItem.mutable,
                                                        removable = updatedItem.removable,
                                                        localCandidates = updatedItem.localCandidates,
                                                        remoteCandidates = updatedItem.remoteCandidates
                                                    )
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Local re-recognition failed", e)
                                        }
                                    }
                                    localJob.join()
                                }
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
                isDataCollectMode = false,
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
                        saveJsonl(
                            context = context,
                            userId = userSettings.userId,
                            filename = File(updatedItem.wavFilePath).nameWithoutExtension,
                            originalText = updatedItem.recognizedText,
                            modifiedText = updatedItem.modifiedText,
                            checked = updatedItem.checked,
                            mutable = updatedItem.mutable,
                            removable = updatedItem.removable,
                            localCandidates = updatedItem.localCandidates,
                            remoteCandidates = updatedItem.remoteCandidates
                        )
                    }
                    transcriptToEditInDialog = null
                },
                onSpeakConfirm = { newText ->
                    handleDialogTtsConfirm(index, newText)
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
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Button(onClick = onRecordingButtonClick, enabled = !isPlaybackActive && !isAsrModelLoading && !isEditing) {
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
