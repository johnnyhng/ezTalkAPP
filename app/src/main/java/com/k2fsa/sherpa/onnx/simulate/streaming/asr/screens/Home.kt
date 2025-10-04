package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.R
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.RecognitionResult
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.SimulateStreamingAsr
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.saveAsWav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private var audioRecord: AudioRecord? = null
private var mediaPlayer: MediaPlayer? = null
// Recreate the channel inside the effect to ensure it's fresh on each start
// private val samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
private const val sampleRateInHz = 16000

@Composable
fun HomeScreen(
    // Inject the ViewModel. It will be automatically created and retained across config changes.
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val activity = LocalContext.current as Activity
    val coroutineScope = rememberCoroutineScope()

    // UI state
    var isStarted by remember { mutableStateOf(false) }
    val resultList = remember { mutableStateListOf<RecognitionResult>() }
    val lazyColumnListState = rememberLazyListState()

    // Collect settings from the ViewModel. `collectAsState` makes the UI react to changes.
    val userSettings by homeViewModel.userSettings.collectAsState()

    // Playback state
    var currentlyPlayingPath by remember { mutableStateOf<String?>(null) }
    val isPlaying by remember { derivedStateOf { currentlyPlayingPath != null } }

    fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentlyPlayingPath = null
    }

    DisposableEffect(Unit) {
        onDispose {
            audioRecord?.release()
            audioRecord = null
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    val onRecordingButtonClick: () -> Unit = {
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
                // Create a new channel every time we start recording
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
                val recordingJob = CoroutineScope(Dispatchers.IO).launch {
                    Log.i(TAG, "Audio recording started")
                    val interval = 0.1 // 100ms
                    val bufferSize = (interval * sampleRateInHz).toInt()
                    val buffer = ShortArray(bufferSize)

                    audioRecord?.startRecording()
                    while (isStarted) {
                        if (isPlaying) {
                            delay(100)
                            continue
                        }
                        val ret = audioRecord?.read(buffer, 0, buffer.size)
                        if (ret != null && ret > 0) {
                            val samples = FloatArray(ret) { i -> buffer[i] / 32768.0f }
                            samplesChannel.send(samples)
                        }
                    }
                    samplesChannel.close() // Close the channel to signal the end
                    Log.i(TAG, "Audio recording stopped")
                }

                // --- Coroutine to process audio ---
                val processingJob = CoroutineScope(Dispatchers.Default).launch {
                    // Use settings from the ViewModel's state
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

                    // Process audio from the channel
                    for (s in samplesChannel) { // This loop will now correctly terminate when channel is closed
                        if (!isStarted) break // Exit loop
                        if (isPlaying) continue

                        buffer.addAll(s.toList())

                        if (isSpeechStarted && !saveVadSegmentsOnly) {
                            fullRecordingBuffer.addAll(s.toList())
                        }

                        while (offset + windowSize < buffer.size) {
                            val chunk = buffer.subList(offset, offset + windowSize).toFloatArray()
                            SimulateStreamingAsr.vad.acceptWaveform(chunk)
                            offset += windowSize
                            if (!isSpeechStarted && SimulateStreamingAsr.vad.isSpeechDetected()) {
                                isSpeechStarted = true
                                startTime = System.currentTimeMillis()

                                if (!saveVadSegmentsOnly) {
                                    fullRecordingBuffer.clear()
                                    fullRecordingBuffer.addAll(buffer)
                                }
                            }
                        }

                        val elapsed = System.currentTimeMillis() - startTime
                        if (isSpeechStarted && elapsed > PARTIAL_INTERVAL_MS) {
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
                                        resultList.add(RecognitionResult(lastText, ""))
                                        added = true
                                    } else {
                                        val lastResult = resultList.last()
                                        resultList[resultList.size - 1] =
                                            lastResult.copy(text = lastText)
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

                        while (!SimulateStreamingAsr.vad.empty()) {
                            val seg = SimulateStreamingAsr.vad.front().samples
                            SimulateStreamingAsr.vad.pop()
                            utteranceSegments.add(seg)
                            lastVadPacketAt = System.currentTimeMillis()
                        }

                        if (utteranceSegments.isNotEmpty()) {
                            val since = System.currentTimeMillis() - lastVadPacketAt
                            if (since >= LINGER_MS) {
                                // Manual concatenation of FloatArray chunks
                                val totalSize = utteranceSegments.sumOf { it.size }
                                val concatenated = FloatArray(totalSize)
                                var currentPos = 0
                                for (segment in utteranceSegments) {
                                    System.arraycopy(
                                        segment,
                                        0,
                                        concatenated,
                                        currentPos,
                                        segment.size
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

                                    val wavPath = saveAsWav(
                                        context = context,
                                        samples = audioToSave,
                                        sampleRate = sampleRateInHz,
                                        numChannels = 1,
                                        filename = "rec_${System.currentTimeMillis()}"
                                    )

                                    val newResult = RecognitionResult(result.text, wavPath ?: "")

                                    if (lastText.isNotBlank()) {
                                        if (added && resultList.isNotEmpty()) {
                                            resultList[resultList.size - 1] = newResult
                                        } else {
                                            resultList.add(newResult)
                                        }
                                    } else {
                                        resultList.add(newResult)
                                    }
                                    coroutineScope.launch {
                                        lazyColumnListState.animateScrollToItem(resultList.size - 1)
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
                            }
                        }
                    }
                }
            }
        } else {
            // This block runs when isStarted becomes false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            stopPlayback()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier) {
            // Delay Slider
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "Delay: ${userSettings.lingerMs.roundToInt()} ms",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Slider(
                    value = userSettings.lingerMs,
                    onValueChange = { homeViewModel.updateLingerMs(it) },
                    valueRange = 0f..10000f,
                    steps = ((10000f - 0f) / 100f).toInt() - 1,
                    enabled = !isStarted && !isPlaying
                )
            }

            // Recognize Time Slider
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "Recognize Time: ${userSettings.partialIntervalMs.roundToInt()} ms",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Slider(
                    value = userSettings.partialIntervalMs,
                    onValueChange = { homeViewModel.updatePartialIntervalMs(it) },
                    valueRange = 200f..1000f,
                    steps = ((1000f - 200f) / 50f).toInt() - 1,
                    enabled = !isStarted && !isPlaying
                )
            }

            // Save Mode Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "Save VAD Segments")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = !userSettings.saveVadSegmentsOnly,
                    onCheckedChange = { isChecked -> homeViewModel.updateSaveVadSegmentsOnly(!isChecked) },
                    enabled = !isStarted && !isPlaying
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save Full Audio")
            }

            HomeButtonRow(
                isStarted = isStarted,
                onRecordingButtonClick = onRecordingButtonClick,
                onCopyButtonClick = {
                    if (resultList.isNotEmpty()) {
                        val s = resultList.mapIndexed { i, result -> "${i + 1}: ${result.text}" }
                            .joinToString(separator = "\n")
                        clipboardManager.setText(AnnotatedString(s))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Nothing to copy", Toast.LENGTH_SHORT).show()
                    }
                },
                onClearButtonClick = {
                    resultList.clear()
                },
                isPlaybackActive = isPlaying
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                state = lazyColumnListState
            ) {
                itemsIndexed(resultList) { index, result ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "${index + 1}: ${result.text}", modifier = Modifier.weight(1f))
                        if (result.wavFilePath.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (currentlyPlayingPath == result.wavFilePath) {
                                        stopPlayback()
                                    } else {
                                        stopPlayback()
                                        currentlyPlayingPath = result.wavFilePath
                                        mediaPlayer = MediaPlayer().apply {
                                            try {
                                                setDataSource(result.wavFilePath)
                                                prepare()
                                                start()
                                                setOnCompletionListener {
                                                    stopPlayback()
                                                }
                                            } catch (e: Exception) {
                                                Log.e(
                                                    TAG,
                                                    "MediaPlayer failed for ${result.wavFilePath}",
                                                    e
                                                )
                                                stopPlayback()
                                            }
                                        }
                                    }
                                },
                                enabled = !isStarted && (!isPlaying || currentlyPlayingPath == result.wavFilePath)
                            ) {
                                Text(text = if (currentlyPlayingPath == result.wavFilePath) "Stop" else "Play")
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
