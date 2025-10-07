package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.R
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.SimulateStreamingAsr
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.saveAsWav
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.saveJsonl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private var audioRecord: AudioRecord? = null
private const val sampleRateInHz = 16000

// This dynamic array will keep records for future feedback implementation.
private val feedbackRecords = mutableListOf<Transcript>()

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val activity = LocalContext.current as Activity
    val coroutineScope = rememberCoroutineScope()

    // UI state
    var isStarted by remember { mutableStateOf(false) }
    val resultList = remember { mutableStateListOf<Transcript>() }
    val lazyColumnListState = rememberLazyListState()

    // State for the Edit Dialog
    var showEditDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Transcript?>(null) }
    var editingItemIndex by remember { mutableStateOf(-1) }
    var originalTextForDialog by remember { mutableStateOf("") }
    var modifiedTextForDialog by remember { mutableStateOf("") }

    // Waveform and countdown states
    var latestAudioSamples by remember { mutableStateOf(FloatArray(0)) }
    var isRecognizingSpeech by remember { mutableStateOf(false) }
    var countdownProgress by remember { mutableStateOf(0f) }


    // Collect settings from the ViewModel
    val userSettings by homeViewModel.userSettings.collectAsState()
    val userId = userSettings.userId

    // Playback state
    val currentlyPlaying by MediaController.currentlyPlaying.collectAsState()
    val isPlaying = currentlyPlaying != null

    // TTS state
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsSpeaking by remember { mutableStateOf(false) }

    // Channel to signal the audio processor to flush remaining buffers
    val flushChannel = remember { Channel<Unit>(Channel.CONFLATED) }

    // Initialize TTS
    LaunchedEffect(key1 = context) {
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
            audioRecord?.release()
            audioRecord = null
            MediaController.stop()
            tts?.stop()
            tts?.shutdown()
            tts = null
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
                        if (isPlaying || isTtsSpeaking) { // Pause recording during playback and TTS
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
                    val LINGER_MS = userSettings.lingerMs
                    val PARTIAL_INTERVAL_MS = userSettings.partialIntervalMs
                    val saveVadSegmentsOnly = userSettings.saveVadSegmentsOnly

                    var buffer = arrayListOf<Float>()
                    var offset = 0
                    val windowSize = 512
                    var isSpeechStarted = false
                    var startTime = System.currentTimeMillis()
                    var lastText = ""
                    var added = false

                    val utteranceSegments = mutableListOf<FloatArray>()
                    var lastVadPacketAt = 0L

                    var fullRecordingBuffer = arrayListOf<Float>()

                    var done = false
                    while (!done) {
                        val s = samplesChannel.tryReceive().getOrNull()
                        val flushRequest = flushChannel.tryReceive().getOrNull()

                        if (s == null) {
                            if (flushRequest != null || (samplesChannel.isClosedForReceive && buffer.isEmpty())) {
                                done =
                                    true // Stop signal or closed channel, prepare for final processing
                            } else {
                                delay(10) // No data, wait briefly
                                // continue
                            }
                        } else {
                            buffer.addAll(s.toList())

                            if (isSpeechStarted && !saveVadSegmentsOnly) {
                                fullRecordingBuffer.addAll(s.toList())
                            }
                        }

                        while (offset + windowSize < buffer.size) {
                            val chunk = buffer.subList(offset, offset + windowSize).toFloatArray()
                            SimulateStreamingAsr.vad.acceptWaveform(chunk)
                            offset += windowSize
                            if (!isSpeechStarted && SimulateStreamingAsr.vad.isSpeechDetected()) {
                                isSpeechStarted = true
                                launch(Dispatchers.Main) { isRecognizingSpeech = true }
                                startTime = System.currentTimeMillis()

                                if (!saveVadSegmentsOnly) {
                                    fullRecordingBuffer.clear()
                                    fullRecordingBuffer.addAll(buffer)
                                }
                            }
                        }

                        if (isSpeechStarted) {
                            val elapsed = System.currentTimeMillis() - startTime
                            if (elapsed > PARTIAL_INTERVAL_MS) {
                                val stream = SimulateStreamingAsr.recognizer.createStream()
                                try {
                                    stream.acceptWaveform(
                                        buffer.subList(0, offset).toFloatArray(),
                                        sampleRateInHz
                                    )
                                    SimulateStreamingAsr.recognizer.decode(stream)
                                    val result = SimulateStreamingAsr.recognizer.getResult(stream)
                                    lastText = result.text

                                    if (lastText.isNotBlank()) {
                                        if (!added || resultList.isEmpty()) {
                                            resultList.add(
                                                Transcript(
                                                    recognizedText = lastText,
                                                    wavFilePath = ""
                                                )
                                            )
                                            added = true
                                        } else {
                                            val lastItem = resultList.last()
                                            resultList[resultList.size - 1] =
                                                lastItem.copy(
                                                    recognizedText = lastText,
                                                    modifiedText = lastText
                                                )
                                        }
                                        coroutineScope.launch {
                                            lazyColumnListState.animateScrollToItem(resultList.size - 1)
                                        }
                                    }
                                } finally {
                                    stream.release()
                                }
                                startTime = System.currentTimeMillis()
                            }
                        }

                        while (!SimulateStreamingAsr.vad.empty()) {
                            val seg = SimulateStreamingAsr.vad.front().samples
                            SimulateStreamingAsr.vad.pop()
                            utteranceSegments.add(seg)
                            lastVadPacketAt = System.currentTimeMillis()
                        }

                        if (utteranceSegments.isNotEmpty()) {
                            val since = System.currentTimeMillis() - lastVadPacketAt
                            val progress = (since.toFloat() / LINGER_MS).coerceIn(0f, 1f)
                            launch(Dispatchers.Main) { countdownProgress = progress }
                            // Process if silence duration is met OR if it's the final flush
                            if (since >= LINGER_MS || (done && utteranceSegments.isNotEmpty())) {
                                val totalSize = utteranceSegments.sumOf { it.size }
                                val concatenated = FloatArray(totalSize)
                                var currentPos = 0
                                for (segment in utteranceSegments) {
                                    System.arraycopy(
                                        segment, 0, concatenated, currentPos, segment.size
                                    )
                                    currentPos += segment.size
                                }
                                val utteranceForRecognition = concatenated

                                val audioToSave = if (saveVadSegmentsOnly) {
                                    utteranceForRecognition
                                } else {
                                    fullRecordingBuffer.toFloatArray()
                                }

                                val stream = SimulateStreamingAsr.recognizer.createStream()
                                try {
                                    stream.acceptWaveform(
                                        utteranceForRecognition,
                                        sampleRateInHz
                                    )
                                    SimulateStreamingAsr.recognizer.decode(stream)
                                    val result = SimulateStreamingAsr.recognizer.getResult(stream)

                                    val timestamp = SimpleDateFormat(
                                        "yyyyMMdd-HHmmss",
                                        Locale.getDefault()
                                    ).format(
                                        Date()
                                    )
                                    val filename = "rec_${timestamp}"

                                    // Save the WAV file
                                    val wavPath = saveAsWav(
                                        context = context,
                                        samples = audioToSave,
                                        sampleRate = sampleRateInHz,
                                        numChannels = 1,
                                        userId = userId,
                                        filename = filename
                                    )

                                    if (wavPath != null) {
                                        // Save the initial JSONL entry
                                        saveJsonl(
                                            context = context,
                                            userId = userId,
                                            filename = filename,
                                            originalText = result.text,
                                            modifiedText = result.text, // Initially, modified is same as original
                                            checked = false
                                        )
                                    }


                                    val newTranscript = Transcript(
                                        recognizedText = result.text,
                                        wavFilePath = wavPath ?: ""
                                    )

                                    if (result.text.isNotBlank()) {
                                        if (added && resultList.isNotEmpty()) {
                                            resultList[resultList.size - 1] = newTranscript
                                        } else {
                                            resultList.add(newTranscript)
                                        }
                                    }

                                    // Add the completed record to our feedback list
                                    if (result.text.isNotBlank() && resultList.isNotEmpty()) {
                                        feedbackRecords.add(resultList.last())
                                    }

                                    coroutineScope.launch {
                                        if (resultList.isNotEmpty()) {
                                            lazyColumnListState.animateScrollToItem(resultList.size - 1)
                                        }
                                    }
                                } finally {
                                    stream.release()
                                }
                                utteranceSegments.clear()
                                fullRecordingBuffer.clear()
                                isSpeechStarted = false
                                buffer = arrayListOf()
                                offset = 0
                                lastText = ""
                                added = false
                                launch(Dispatchers.Main) {
                                    isRecognizingSpeech = false
                                    countdownProgress = 0f
                                }
                            }
                        } else {
                            if (isRecognizingSpeech) {
                                val sinceLastPacket = System.currentTimeMillis() - lastVadPacketAt
                                if (lastVadPacketAt != 0L && sinceLastPacket > LINGER_MS) {
                                    launch(Dispatchers.Main) { countdownProgress = 0f }
                                }
                            }
                        }
                    }
                    launch(Dispatchers.Main) {
                        isRecognizingSpeech = false
                        countdownProgress = 0f
                    }
                }
            }
        } else {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            MediaController.stop()
        }
    }

    if (showEditDialog && editingItemIndex != -1) {
        EditRecognitionDialog(
            originalText = originalTextForDialog,
            currentText = modifiedTextForDialog,
            wavFilePath = editingItem?.wavFilePath ?: "", // Pass the wav file path
            onDismiss = { showEditDialog = false },
            onConfirm = { newText ->
                val oldItem = resultList[editingItemIndex]
                resultList[editingItemIndex] = oldItem.copy(modifiedText = newText)

                // *** MODIFIED: Save the edit to the JSONL file ***
                if (oldItem.wavFilePath.isNotEmpty()) {
                    val file = File(oldItem.wavFilePath)
                    val filename = file.nameWithoutExtension
                    coroutineScope.launch(Dispatchers.IO) {
                        saveJsonl(
                            context = context,
                            userId = userId,
                            filename = filename,
                            originalText = oldItem.recognizedText,
                            modifiedText = newText,
                            checked = oldItem.checked
                        )
                    }
                }
                showEditDialog = false
            }
        )
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
                    Log.i(TAG, "Feedback records cleared. Count: ${feedbackRecords.size}")
                },
                isPlaybackActive = isPlaying || isTtsSpeaking
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                state = lazyColumnListState
            ) {
                itemsIndexed(resultList, key = { _, item -> item.hashCode() }) { index, result ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${index + 1}: ${result.modifiedText}",
                            modifier = Modifier.weight(1f)
                        )
                        if (index == resultList.size - 1 && isRecognizingSpeech && countdownProgress > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                progress = countdownProgress,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        if (result.wavFilePath.isNotEmpty()) {
                            // Talk Button -> IconButton
                            IconButton(
                                onClick = {
                                    MediaController.stop() // Stop any audio playback
                                    val utteranceId = UUID.randomUUID().toString()
                                    tts?.speak(
                                        result.modifiedText,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        utteranceId
                                    )
                                    // Update the checked status
                                    val updatedItem = result.copy(checked = true)
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
                                            checked = updatedItem.checked
                                        )
                                    }
                                },
                                enabled = !isStarted && !isPlaying && !isTtsSpeaking
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "Talk"
                                )
                            }

                            // Edit Button -> IconButton
                            IconButton(
                                onClick = {
                                    editingItemIndex = index
                                    editingItem = result // Store the whole item
                                    originalTextForDialog = result.recognizedText
                                    modifiedTextForDialog = result.modifiedText
                                    showEditDialog = true
                                },
                                enabled = !isStarted && !isPlaying && !isTtsSpeaking
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit"
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
                                enabled = !isStarted && !isTtsSpeaking && (!isPlaying || currentlyPlaying == result.wavFilePath)
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
}

@SuppressLint("UnrememberedMutableState")
@Composable
private fun HomeButtonRow(
    modifier: Modifier = Modifier,
    isStarted: Boolean,
    onRecordingButtonClick: () -> Unit,
    onCopyButtonClick: () -> Unit,
    onClearButtonClick: () -> Unit,
    isPlaybackActive: Boolean
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = onRecordingButtonClick,
            enabled = !isPlaybackActive
        ) {
            Text(text = stringResource(if (isStarted) R.string.stop else R.string.start))
        }
        Spacer(modifier = Modifier.width(24.dp))
        Button(
            onClick = onCopyButtonClick,
            enabled = !isStarted && !isPlaybackActive
        ) {
            Text(text = stringResource(id = R.string.copy))
        }
        Spacer(modifier = Modifier.width(24.dp))
        Button(
            onClick = onClearButtonClick,
            enabled = !isStarted && !isPlaybackActive
        ) {
            Text(text = stringResource(id = R.string.clear))
        }
    }
}

@Composable
fun WaveformDisplay(samples: FloatArray, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier) {
        drawWaveform(samples, color)
    }
}

private fun DrawScope.drawWaveform(samples: FloatArray, color: Color) {
    if (samples.isEmpty()) return

    val width = size.width
    val height = size.height
    val centerY = height / 2

    val step = width / samples.size
    val maxAmplitude = samples.maxOrNull() ?: 1f

    for (i in 0 until samples.size - 1) {
        val x1 = i * step
        val y1 = centerY - (samples[i] / maxAmplitude * centerY)

        val x2 = (i + 1) * step
        val y2 = centerY - (samples[i + 1] / maxAmplitude * centerY)

        drawLine(
            color = color,
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = 2f
        )
    }
}
