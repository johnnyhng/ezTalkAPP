package tw.com.johnnyhng.eztalk.asr.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
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
import tw.com.johnnyhng.eztalk.asr.SimulateStreamingAsr
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.widgets.EditRecognitionDialog
import tw.com.johnnyhng.eztalk.asr.widgets.EditableDropdown
import tw.com.johnnyhng.eztalk.asr.widgets.WaveformDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.utils.MediaController
import tw.com.johnnyhng.eztalk.asr.utils.getRemoteCandidates
import tw.com.johnnyhng.eztalk.asr.utils.readWavFileToFloatArray
import tw.com.johnnyhng.eztalk.asr.utils.saveAsWav
import tw.com.johnnyhng.eztalk.asr.utils.saveJsonl
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private var audioRecord: AudioRecord? = null
private const val sampleRateInHz = 16000

// This dynamic array will keep records for future feedback implementation.
private val feedbackRecords = mutableListOf<Transcript>()

@Composable
fun TranslateScreen(
    homeViewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val activity = LocalContext.current as Activity
    val coroutineScope = rememberCoroutineScope()

    // UI state
    var isStarted by remember { mutableStateOf(false) }
    val resultList = remember { mutableStateListOf<Transcript>() }
    val lazyColumnListState = rememberLazyListState()

    // State for inline editing
    var isEditing by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var editingText by remember { mutableStateOf("") }
    var localCandidate by remember { mutableStateOf<String?>(null) }
    var isFetchingCandidates by remember { mutableStateOf(false) }
    var transcriptToEditInDialog by remember { mutableStateOf<Pair<Int, Transcript>?>(null) }

    // Waveform and recognition states
    var latestAudioSamples by remember { mutableStateOf(FloatArray(0)) }
    var isRecognizingSpeech by remember { mutableStateOf(false) }

    // Collect settings from the ViewModel
    val userSettings by homeViewModel.userSettings.collectAsState()
    val userId = userSettings.userId

    // Playback state
    val currentlyPlaying by MediaController.currentlyPlaying.collectAsState()

    // TTS state
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsSpeaking by remember { mutableStateOf(false) }

    // ASR model loading state
    var isAsrModelLoading by remember { mutableStateOf(true) }

    // Channel to signal the audio processor to flush remaining buffers
    val flushChannel = remember { Channel<Unit>(Channel.CONFLATED) }
    val recognitionQueue = remember { Channel<String>(Channel.UNLIMITED) }

    // Initialize recognizer in the background
    LaunchedEffect(key1 = homeViewModel.selectedModel) {
        isAsrModelLoading = true
        Log.i(TAG, "ASR model initialization started.")
        withContext(Dispatchers.IO) {
            SimulateStreamingAsr.initOfflineRecognizer(
                context.assets,
                homeViewModel.selectedModel
            )
        }
        isAsrModelLoading = false
        Log.i(TAG, "ASR model initialization finished.")
    }

    // Background recognition queue processor
    LaunchedEffect(userSettings.recognitionUrl, userSettings.userId) {
        launch(Dispatchers.IO) {
            for (wavPath in recognitionQueue) {
                if (userSettings.recognitionUrl.isBlank()) continue

                var transcript: Transcript? = null
                // Switch to main thread to safely access resultList
                withContext(Dispatchers.Main) {
                    transcript = resultList.find { it.wavFilePath == wavPath }
                }

                transcript?.let {
                    val sentences = getRemoteCandidates(
                        context = context,
                        wavFilePath = wavPath,
                        userId = userSettings.userId,
                        recognitionUrl = userSettings.recognitionUrl,
                        originalText = it.recognizedText,
                        currentText = it.modifiedText
                    )

                    if (sentences.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val index =
                                resultList.indexOfFirst { transcript -> transcript.wavFilePath == wavPath }
                            if (index != -1) {
                                resultList[index] =
                                    resultList[index].copy(remoteCandidates = sentences)
                            }
                        }
                    }
                } ?: Log.w(
                    TAG,
                    "Could not find transcript for wavPath: $wavPath to update remote candidates."
                )
            }
        }
    }

    // Initialize TTS
    LaunchedEffect(key1 = Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                Log.i(TAG, "TTS initialized successfully.")
            } else {
                Log.e(TAG, "TTS initialization failed.")
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isTtsSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                isTtsSpeaking = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isTtsSpeaking = false
            }
        })
    }


    DisposableEffect(Unit) {
        onDispose {
            stopAudio()
            tts?.stop()
            tts?.shutdown()
            tts = null
            recognitionQueue.close()
        }
    }

    val onRecordingButtonClick: () -> Unit = {
        if (isStarted) { // User is clicking "Stop"
            coroutineScope.launch {
                flushChannel.send(Unit) // Signal the processor to flush
            }
        }
        isStarted = !isStarted
    }

    LaunchedEffect(isStarted, userSettings) {
        if (isStarted) {
            if (ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Recording is not allowed")
                isStarted = false
            } else {
                stopAudio()
                val samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)

                val audioSource = MediaRecorder.AudioSource.MIC
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val numBytes =
                    AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
                audioRecord = AudioRecord(
                    audioSource,
                    sampleRateInHz,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    numBytes * 2
                )
                SimulateStreamingAsr.vad.reset()

                // --- Coroutine to record audio ---
                CoroutineScope(Dispatchers.IO).launch {
                    Log.i(TAG, "Audio recording started")
                    val interval = 0.1 // 100ms
                    val bufferSize = (interval * sampleRateInHz).toInt()
                    val buffer = ShortArray(bufferSize)

                    audioRecord?.startRecording()
                    while (isStarted) {
                        if (currentlyPlaying != null || isTtsSpeaking) { // Pause recording during playback and TTS
                            delay(100)
                            continue
                        }
                        val ret = audioRecord?.read(buffer, 0, buffer.size)
                        if (ret != null && ret > 0) {
                            val samples = FloatArray(ret) { i -> buffer[i] / 32768.0f }
                            samplesChannel.send(samples)
                            launch(Dispatchers.Main) {
                                latestAudioSamples = samples
                            }
                        }
                    }
                    samplesChannel.close() // Close the channel to signal the end
                    Log.i(TAG, "Audio recording stopped")
                }

                // --- Coroutine to process audio ---
                CoroutineScope(Dispatchers.Default).launch {
                    val fullRecordingBuffer = arrayListOf<Float>()
                    val reserveForPreviousSpeechDetectedMs = 500
                    val keep = (sampleRateInHz / 1000) * reserveForPreviousSpeechDetectedMs

                    var speechStartOffset = -1
                    var lastSpeechDetectedOffset = -1
                    var isSpeechStarted = false

                    var done = false
                    while (!done) {
                        val s = samplesChannel.tryReceive().getOrNull()
                        val flushRequest = flushChannel.tryReceive().getOrNull()

                        if (s != null) {
                            val currentBufferPosition = fullRecordingBuffer.size
                            fullRecordingBuffer.addAll(s.toList())
                            SimulateStreamingAsr.vad.acceptWaveform(s)

                            if (!isSpeechStarted && SimulateStreamingAsr.vad.isSpeechDetected()) {
                                isSpeechStarted = true
                                speechStartOffset = max(0, currentBufferPosition - keep)
                            }
                        }

                        while (!SimulateStreamingAsr.vad.empty()) {
                            lastSpeechDetectedOffset = fullRecordingBuffer.size
                            SimulateStreamingAsr.vad.pop()
                        }

                        if (flushRequest != null || (samplesChannel.isClosedForReceive && s == null)) {
                            done = true
                        } else if (s == null) {
                            delay(10)
                        }
                    }

                    // Final recognition and save
                    if (isSpeechStarted && speechStartOffset != -1) {
                        withContext(Dispatchers.Main) { isRecognizingSpeech = true }

                        val endPosition = if (lastSpeechDetectedOffset != -1) {
                            min(fullRecordingBuffer.size, lastSpeechDetectedOffset + keep)
                        } else {
                            fullRecordingBuffer.size
                        }

                        val audioToRecognize = if (speechStartOffset < endPosition) {
                            fullRecordingBuffer.subList(speechStartOffset, endPosition).toFloatArray()
                        } else {
                            FloatArray(0)
                        }

                        if (audioToRecognize.isNotEmpty()) {
                            val stream = SimulateStreamingAsr.recognizer.createStream()
                            try {
                                stream.acceptWaveform(audioToRecognize, sampleRateInHz)
                                SimulateStreamingAsr.recognizer.decode(stream)
                                val result = SimulateStreamingAsr.recognizer.getResult(stream)

                                if (result.text.isNotBlank()) {
                                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
                                    val filename = "${timestamp}.app"

                                    val wavPath = saveAsWav(
                                        context = context,
                                        samples = audioToRecognize,
                                        sampleRate = sampleRateInHz,
                                        numChannels = 1,
                                        userId = userId,
                                        filename = filename
                                    )

                                    val newTranscript = Transcript(
                                        recognizedText = result.text,
                                        wavFilePath = wavPath ?: "",
                                        modifiedText = result.text
                                    )

                                    withContext(Dispatchers.Main) {
                                        resultList.add(newTranscript)
                                        if (wavPath != null) {
                                            saveJsonl(
                                                context = context,
                                                userId = userId,
                                                filename = filename,
                                                originalText = newTranscript.recognizedText,
                                                modifiedText = newTranscript.modifiedText,
                                                checked = false
                                            )

                                            if (userSettings.recognitionUrl.isNotBlank()) {
                                                //recognitionQueue.trySend(wavPath)
                                            }
                                        }
                                        feedbackRecords.add(newTranscript)
                                        coroutineScope.launch {
                                            if (resultList.isNotEmpty()) {
                                                lazyColumnListState.animateScrollToItem(resultList.size - 1)
                                            }
                                        }
                                    }
                                }
                            } finally {
                                stream.release()
                            }
                        }
                        withContext(Dispatchers.Main) { isRecognizingSpeech = false }
                    }
                }
            }
        } else {
            stopAudio()
        }
    }


    // Function to handle TTS and transcript updates
    fun handleTtsClick(index: Int, text: String) {
        MediaController.stop() // Stop any audio playback
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
        // Update the checked status
        val updatedItem = resultList[index].copy(modifiedText = text, checked = true)
        resultList[index] = updatedItem

        // Save the updated record to JSONL
        val file = File(updatedItem.wavFilePath)
        val filename = file.nameWithoutExtension
        coroutineScope.launch(Dispatchers.IO) {
            saveJsonl(
                context = context,
                userId = userId,
                filename = filename,
                originalText = updatedItem.recognizedText,
                modifiedText = updatedItem.modifiedText,
                checked = updatedItem.checked,
                remoteCandidates = updatedItem.remoteCandidates
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier) {
            HomeButtonRow(
                modifier = Modifier.padding(vertical = 16.dp),
                isStarted = isStarted,
                onRecordingButtonClick = onRecordingButtonClick,
                onCopyButtonClick = {
                    if (resultList.isNotEmpty()) {
                        val s =
                            resultList.mapIndexed { i, result -> "${i + 1}: ${result.modifiedText}" }
                                .joinToString(separator = "\n")
                        clipboardManager.setText(AnnotatedString(s))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Nothing to copy", Toast.LENGTH_SHORT).show()
                    }
                },
                onClearButtonClick = {
                    resultList.clear()
                    feedbackRecords.clear() // Also clear the feedback records
                    Log.i(
                        TAG,
                        "Feedback records cleared. Count: ${feedbackRecords.size}"
                    )
                },
                isPlaybackActive = currentlyPlaying != null || isTtsSpeaking,
                isAsrModelLoading = isAsrModelLoading,
                isEditing = isEditing
            )
            if (isStarted) {
                WaveformDisplay(
                    samples = latestAudioSamples,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(vertical = 8.dp)
                )
            }
            if (isRecognizingSpeech) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                state = lazyColumnListState
            ) {
                itemsIndexed(resultList, key = { _, item -> item.hashCode() }) { index, result ->
                    if (editingIndex == index) {
                        // Inline editing UI
                        val menuItems = remember(result, localCandidate) {
                            (listOfNotNull(
                                result.recognizedText,
                                result.modifiedText,
                                localCandidate
                            ) + result.remoteCandidates).distinct()
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            EditableDropdown(
                                value = editingText,
                                onValueChange = { editingText = it },
                                label = { Text("Edit") },
                                menuItems = menuItems,
                                isRecognizing = isFetchingCandidates,
                                modifier = Modifier.weight(1f),
                                startExpanded = true
                            )
                            IconButton(onClick = {
                                // Reset editing state
                                isEditing = false
                                editingIndex = -1
                                editingText = ""
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel Edit"
                                )
                            }
                            IconButton(onClick = {
                                handleTtsClick(index, editingText)

                                // Reset editing state
                                isEditing = false
                                editingIndex = -1
                                editingText = ""
                            }) {
                                Icon(
                                    Icons.Default.RecordVoiceOver,
                                    contentDescription = "Confirm Edit"
                                )
                            }
                        }
                    } else {
                        // Normal display Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isRecognizingSpeech && !isEditing && !isStarted) {
                                    if (userSettings.inlineEdit) {
                                        isEditing = true
                                        editingIndex = index
                                        editingText = result.modifiedText
                                        localCandidate = null // Clear old local candidate

                                        if (result.wavFilePath.isNotEmpty()) {
                                            coroutineScope.launch {
                                                isFetchingCandidates = true
                                                val localJob = launch(Dispatchers.IO) {
                                                    try {
                                                        val audioData =
                                                            readWavFileToFloatArray(result.wavFilePath)
                                                        if (audioData != null) {
                                                            val recognizer =
                                                                SimulateStreamingAsr.recognizer
                                                            val stream =
                                                                recognizer.createStream()
                                                            stream.acceptWaveform(
                                                                audioData,
                                                                sampleRateInHz
                                                            )
                                                            recognizer.decode(stream)
                                                            val localResultText =
                                                                recognizer
                                                                    .getResult(stream).text
                                                            stream.release()
                                                            withContext(Dispatchers.Main) {
                                                                localCandidate = localResultText
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e(
                                                            TAG,
                                                            "Local re-recognition failed",
                                                            e
                                                        )
                                                    }
                                                }

                                                localJob.join()
                                                isFetchingCandidates = false
                                            }
                                        }
                                    } else {
                                        isEditing = true
                                        transcriptToEditInDialog = index to result
                                    }
                                }
                        ) {
                            Text(
                                text = "${index + 1}: ${result.modifiedText}",
                                modifier = Modifier.weight(1f)
                            )

                            if (result.wavFilePath.isNotEmpty()) {
                                // Talk Button -> IconButton
                                IconButton(
                                    onClick = {
                                        handleTtsClick(index, result.modifiedText)
                                    },
                                    enabled = !isStarted && currentlyPlaying == null && !isTtsSpeaking && !isEditing
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = "Talk"
                                    )
                                }

                                // Play Button -> IconButton
                                IconButton(
                                    onClick = {
                                        if (currentlyPlaying == result.wavFilePath) {
                                            MediaController.stop()
                                        } else {
                                            MediaController.play(result.wavFilePath)
                                        }
                                    },
                                    enabled = !isStarted && !isTtsSpeaking && (currentlyPlaying == null || currentlyPlaying == result.wavFilePath) && !isEditing
                                ) {
                                    Icon(
                                        imageVector = if (currentlyPlaying == result.wavFilePath) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = if (currentlyPlaying == result.wavFilePath) "Stop" else "Play"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        transcriptToEditInDialog?.let { (index, transcript) ->
            EditRecognitionDialog(
                originalText = transcript.recognizedText,
                currentText = transcript.modifiedText,
                wavFilePath = transcript.wavFilePath,
                onDismiss = {
                    transcriptToEditInDialog = null
                    isEditing = false
                },
                onConfirm = { newText ->
                    val updatedItem = transcript.copy(modifiedText = newText, checked = true)
                    resultList[index] = updatedItem

                    val file = File(updatedItem.wavFilePath)
                    val filename = file.nameWithoutExtension
                    coroutineScope.launch(Dispatchers.IO) {
                        saveJsonl(
                            context = context,
                            userId = userId,
                            filename = filename,
                            originalText = updatedItem.recognizedText,
                            modifiedText = updatedItem.modifiedText,
                            checked = updatedItem.checked,
                            remoteCandidates = updatedItem.remoteCandidates
                        )
                    }

                    transcriptToEditInDialog = null
                    isEditing = false
                },
                userId = userSettings.userId,
                recognitionUrl = userSettings.recognitionUrl,
            )
        }
    }
}

private fun stopAudio() {
    audioRecord?.stop()
    audioRecord?.release()
    audioRecord = null
    MediaController.stop()
}

@SuppressLint("UnrememberedMutableState")
@Composable
private fun HomeButtonRow(
    modifier: Modifier = Modifier,
    isStarted: Boolean,
    onRecordingButtonClick: () -> Unit,
    onCopyButtonClick: () -> Unit,
    onClearButtonClick: () -> Unit,
    isPlaybackActive: Boolean,
    isAsrModelLoading: Boolean,
    isEditing: Boolean,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = onRecordingButtonClick,
            enabled = !isPlaybackActive && !isAsrModelLoading && !isEditing
        ) {
            Text(text = stringResource(if (isStarted) R.string.stop else R.string.start))
        }
        Spacer(modifier = Modifier.width(24.dp))
        Button(
            onClick = onCopyButtonClick,
            enabled = !isStarted && !isPlaybackActive && !isEditing
        ) {
            Text(text = stringResource(id = R.string.copy))
        }
        Spacer(modifier = Modifier.width(24.dp))
        Button(
            onClick = onClearButtonClick,
            enabled = !isStarted && !isPlaybackActive && !isEditing
        ) {
            Text(text = stringResource(id = R.string.clear))
        }
    }
}
