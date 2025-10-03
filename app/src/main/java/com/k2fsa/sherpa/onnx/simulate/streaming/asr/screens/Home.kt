package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.SimulateStreamingAsr
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private var audioRecord: AudioRecord? = null

private const val sampleRateInHz = 16000
private var samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val activity = LocalContext.current as Activity
    var isStarted by remember { mutableStateOf(false) }
    val resultList: MutableList<String> = remember { mutableStateListOf() }
    val lazyColumnListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var lingerMs by remember { mutableFloatStateOf(3000f) }
    var partialIntervalMs by remember { mutableFloatStateOf(300f) }

    val onRecordingButtonClick: () -> Unit = {
        isStarted = !isStarted
        if (isStarted) {
            if (ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Recording is not allowed")
            } else {
                // recording is allowed
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
                    numBytes * 2 // a sample has two bytes as we are using 16-bit PCM
                )

                SimulateStreamingAsr.vad.reset()

                CoroutineScope(Dispatchers.IO).launch {
                    Log.i(TAG, "processing samples")
                    val interval = 0.1 // i.e., 100 ms
                    val bufferSize = (interval * sampleRateInHz).toInt() // in samples
                    val buffer = ShortArray(bufferSize)

                    audioRecord?.let { it ->
                        it.startRecording()

                        while (isStarted) {
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

                    // ★ 使用滑桿狀態
                    val utteranceSegments = mutableListOf<FloatArray>()
                    var lastVadPacketAt = 0L
                    val LINGER_MS = lingerMs.toLong()          // 可調：靜默多久視為一句話結束
                    val PARTIAL_INTERVAL_MS = partialIntervalMs.toLong() // 可調：暫時字幕更新頻率

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
                        for (s in samplesChannel) {
                            if (s.isEmpty()) break

                            buffer.addAll(s.toList())

                            // 送進 VAD（以 512 為窗）
                            while (offset + windowSize < buffer.size) {
                                SimulateStreamingAsr.vad.acceptWaveform(
                                    buffer.subList(offset, offset + windowSize).toFloatArray()
                                )
                                offset += windowSize
                                if (!isSpeechStarted && SimulateStreamingAsr.vad.isSpeechDetected()) {
                                    isSpeechStarted = true
                                    startTime = System.currentTimeMillis()
                                }
                            }

                            // 暫時字幕：每 PARTIAL_INTERVAL_MS 更新一次
                            val elapsed = System.currentTimeMillis() - startTime
                            if (isSpeechStarted && elapsed > PARTIAL_INTERVAL_MS) {
                                val stream = SimulateStreamingAsr.recognizer.createStream()
                                try {
                                    // 用「目前累積至 offset」的資料做即時解碼
                                    stream.acceptWaveform(
                                        buffer.subList(0, offset).toFloatArray(),
                                        sampleRateInHz
                                    )
                                    SimulateStreamingAsr.recognizer.decode(stream)
                                    val result = SimulateStreamingAsr.recognizer.getResult(stream)
                                    lastText = result.text

                                    if (lastText.isNotBlank()) {
                                        if (!added || resultList.isEmpty()) {
                                            resultList.add(lastText)
                                            added = true
                                        } else {
                                            resultList[resultList.size - 1] = lastText
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

                            // ★ 改動點 1：把 VAD 產生的「完成小段」先收集起來，不急著做最終解碼
                            while (!SimulateStreamingAsr.vad.empty()) {
                                val seg = SimulateStreamingAsr.vad.front().samples
                                SimulateStreamingAsr.vad.pop()
                                utteranceSegments.add(seg)
                                lastVadPacketAt = System.currentTimeMillis()
                            }

                            // ★ 改動點 2：若一段時間沒有新 VAD 片段，視為一句話結束 → 合併後做最終解碼
                            if (utteranceSegments.isNotEmpty()) {
                                val since = System.currentTimeMillis() - lastVadPacketAt
                                if (since >= LINGER_MS) {
                                    val utterance = concatChunks(utteranceSegments)

                                    val stream = SimulateStreamingAsr.recognizer.createStream()
                                    try {
                                        stream.acceptWaveform(utterance, sampleRateInHz)
                                        SimulateStreamingAsr.recognizer.decode(stream)
                                        val result = SimulateStreamingAsr.recognizer.getResult(stream)

                                        // 最終結果覆蓋暫時結果或新增一行
                                        if (lastText.isNotBlank()) {
                                            if (added && resultList.isNotEmpty()) {
                                                resultList[resultList.size - 1] = result.text
                                            } else {
                                                resultList.add(result.text)
                                            }
                                        } else {
                                            // 沒有暫時結果也直接新增
                                            resultList.add(result.text)
                                        }
                                        coroutineScope.launch {
                                            lazyColumnListState.animateScrollToItem(resultList.size - 1)
                                        }
                                    } finally {
                                        stream.release()
                                    }

                                    // Reset for next utterance
                                    utteranceSegments.clear()
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
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier) {
            // Delay Slider
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = "Delay: ${lingerMs.roundToInt()} ms", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Slider(
                    value = lingerMs,
                    onValueChange = { lingerMs = it },
                    valueRange = 0f..10000f,
                    steps = ((10000f - 0f) / 100f).toInt() - 1,
                    enabled = !isStarted // Disable when recording
                )
            }

            // Recognize Time Slider
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = "Recognize Time: ${partialIntervalMs.roundToInt()} ms", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Slider(
                    value = partialIntervalMs,
                    onValueChange = { partialIntervalMs = it },
                    valueRange = 200f..1000f,
                    steps = ((1000f - 200f) / 50f).toInt() - 1,
                    enabled = !isStarted // Disable when recording
                )
            }

            HomeButtonRow(
                isStarted = isStarted,
                onRecordingButtonClick = onRecordingButtonClick,
                onCopyButtonClick = {
                    if (resultList.isNotEmpty()) {
                        val s = resultList.mapIndexed { i, s -> "${i + 1}: $s" }
                            .joinToString(separator = "\n")
                        clipboardManager.setText(AnnotatedString(s))

                        Toast.makeText(
                            context,
                            "Copied to clipboard",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    } else {
                        Toast.makeText(
                            context,
                            "Nothing to copy",
                            Toast.LENGTH_SHORT
                        )
                            .show()

                    }
                },
                onClearButtonClick = {
                    resultList.clear()
                }
            )

            if (resultList.size > 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(16.dp),
                    state = lazyColumnListState
                ) {
                    itemsIndexed(resultList) { index, line ->
                        Text(text = "${index+1}: $line")
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
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = onRecordingButtonClick
        ) {
            Text(text = stringResource(if (isStarted) R.string.stop else R.string.start))
        }

        Spacer(modifier = Modifier.width(24.dp))

        Button(onClick = onCopyButtonClick) {
            Text(text = stringResource(id = R.string.copy))
        }

        Spacer(modifier = Modifier.width(24.dp))

        Button(onClick = onClearButtonClick) {
            Text(text = stringResource(id = R.string.clear))
        }
    }
}
