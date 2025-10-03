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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

private const val sampleRateInHz = 16000
private var samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
private var mediaPlayer: MediaPlayer? = null

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val activity = LocalContext.current as Activity
    var isStarted by remember { mutableStateOf(false) }
    val resultList: MutableList<RecognitionResult> = remember { mutableStateListOf() }
    val lazyColumnListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var lingerMs by remember { mutableFloatStateOf(3000f) }
    var partialIntervalMs by remember { mutableFloatStateOf(300f) }

    // ★ New state for the switch
    var saveVadSegmentsOnly by remember { mutableStateOf(false) }

    var currentlyPlayingPath by remember { mutableStateOf<String?>(null) }
    val isPlaying = currentlyPlayingPath != null

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentlyPlayingPath = null
    }

    val onRecordingButtonClick: () -> Unit = {
        isStarted = !isStarted
        if (isStarted) {
            if (ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Recording is not allowed")
                isStarted = false
            } else {
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

                CoroutineScope(Dispatchers.IO).launch {
                    Log.i(TAG, "processing samples")
                    val interval = 0.1
                    val bufferSize = (interval * sampleRateInHz).toInt()
                    val buffer = ShortArray(bufferSize)

                    audioRecord?.let { it ->
                        it.startRecording()

                        while (isStarted) {
                            if (isPlaying) {
                                delay(100)
                                continue
                            }
                            val ret = audioRecord?.read(buffer, 0, buffer.size)
                            ret?.let { n ->
                                val samples = FloatArray(n) { buffer[it] / 32768.0f }
                                samplesChannel.send(samples)
                            }
                        }
                        val samples = FloatArray(0)
                        samplesChannel.send(samples)
                    }
                }

                CoroutineScope(Dispatchers.Default).launch {
                    var buffer = arrayListOf<Float>()
                    var offset = 0
                    val windowSize = 512
                    var isSpeechStarted = false
                    var startTime = System.currentTimeMillis()
                    var lastText = ""
                    var added = false

                    val utteranceSegments = mutableListOf<FloatArray>()
                    var lastVadPacketAt = 0L

                    // ★ Buffer for saving the entire audio stream for an utterance
                    var fullRecordingBuffer = arrayListOf<Float>()

                    fun concatChunks(chunks: List<FloatArray>): FloatArray {
                        val total = chunks.sumOf { it.size }
                        val out = FloatArray(total)
                        var pos = 0
                        for (c in chunks) {
                            c.copyInto(out, pos)
                            pos += c.size
                        }
                        return out
                    }

                    while (isStarted) {
                        val LINGER_MS = lingerMs.toLong()
                        val PARTIAL_INTERVAL_MS = partialIntervalMs.toLong()

                        for (s in samplesChannel) {
                            if (s.isEmpty()) break
                            if (isPlaying) continue

                            buffer.addAll(s.toList())

                            // ★ If speech has started and we need to save the full stream, add samples to the full buffer
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

                                    // ★ Start capturing full audio stream if needed
                                    if (!saveVadSegmentsOnly) {
                                        fullRecordingBuffer.clear()
                                        // Add the buffer content that triggered the speech detection
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
                                            resultList[resultList.size - 1] = lastResult.copy(text = lastText)
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
                                    // ★ Determine which audio data to save
                                    val audioToSave = if (saveVadSegmentsOnly) {
                                        concatChunks(utteranceSegments)
                                    } else {
                                        fullRecordingBuffer.toFloatArray()
                                    }

                                    // Use the VAD segments for final recognition, as it's cleaner
                                    val utteranceForRecognition = concatChunks(utteranceSegments)

                                    val stream = SimulateStreamingAsr.recognizer.createStream()
                                    try {
                                        stream.acceptWaveform(utteranceForRecognition, sampleRateInHz)
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
                                    // Reset for next utterance
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
            }
        } else {
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
                Text(text = "Delay: ${lingerMs.roundToInt()} ms", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Slider(
                    value = lingerMs,
                    onValueChange = { lingerMs = it },
                    valueRange = 0f..10000f,
                    steps = ((10000f - 0f) / 100f).toInt() - 1,
                    enabled = !isStarted && !isPlaying
                )
            }

            // Recognize Time Slider
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(text = "Recognize Time: ${partialIntervalMs.roundToInt()} ms", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Slider(
                    value = partialIntervalMs,
                    onValueChange = { partialIntervalMs = it },
                    valueRange = 200f..1000f,
                    steps = ((1000f - 200f) / 50f).toInt() - 1,
                    enabled = !isStarted && !isPlaying
                )
            }

            // ★ Save Mode Switch
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
                    checked = !saveVadSegmentsOnly,
                    onCheckedChange = { saveVadSegmentsOnly = !it },
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

            if (resultList.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
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
                                                    Log.e(TAG, "MediaPlayer failed for ${result.wavFilePath}", e)
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
            enabled = !isPlaybackActive
        ) {
            Text(text = stringResource(id = R.string.copy))
        }
        Spacer(modifier = Modifier.width(24.dp))
        Button(
            onClick = onClearButtonClick,
            enabled = !isPlaybackActive
        ) {
            Text(text = stringResource(id = R.string.clear))
        }
    }
}
