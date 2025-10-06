package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.R
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.SimulateStreamingAsr
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.saveAsWav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.exists
import kotlin.math.roundToInt

private var audioRecord: AudioRecord? = null
private var mediaPlayer: MediaPlayer? = null
private const val sampleRateInHz = 16000

/**
 * Data class to store the recognized text, the (potentially) modified text,
 * and the path to the associated audio file.
 */
data class Transcript(
    val recognizedText: String,
    var modifiedText: String = recognizedText,
    var wavFilePath: String, // Made this a 'var' to allow updates
)

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


    // Collect settings from the ViewModel
    val userSettings by homeViewModel.userSettings.collectAsState()

    // *** MODIFIED: Initialize userId and store it if it's new ***
    LaunchedEffect(userSettings.userId) {
        if (userSettings.userId == null) {
            homeViewModel.updateUserId("user")
        }
    }
    val userId = userSettings.userId ?: "user" // Use "user" as a fallback

    // Playback state
    var currentlyPlayingPath by remember { mutableStateOf<String?>(null) }
    val isPlaying by remember { derivedStateOf { currentlyPlayingPath != null } }

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

                                    // *** MODIFIED: Use the new saveAsWav from WavUtil.kt with structured path ***
                                    val wavPath = saveAsWav(
                                        context = context,
                                        samples = audioToSave,
                                        sampleRate = sampleRateInHz,
                                        numChannels = 1,
                                        userId = userId,
                                        text = result.text, // Use recognized text for initial directory
                                        filename = "${result.text}-rec_${timestamp}"
                                    )

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

    if (showEditDialog && editingItemIndex != -1) {
        EditRecognitionDialog(
            originalText = originalTextForDialog,
            currentText = modifiedTextForDialog,
            wavFilePath = editingItem?.wavFilePath ?: "", // Pass the wav file path
            onDismiss = { showEditDialog = false },
            onConfirm = { newText ->
                val oldItem = resultList[editingItemIndex]
                resultList[editingItemIndex] = oldItem.copy(modifiedText = newText)
                showEditDialog = false
            }
        )
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
                    enabled = !isStarted && !isPlaying && !isTtsSpeaking
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
                    enabled = !isStarted && !isPlaying && !isTtsSpeaking
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
                    enabled = !isStarted && !isPlaying && !isTtsSpeaking
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save Full Audio")
            }

            HomeButtonRow(
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

                        if (result.wavFilePath.isNotEmpty()) {
                            // Talk Button -> IconButton
                            IconButton(
                                onClick = {
                                    stopPlayback() // Stop any audio playback

                                    // *** MODIFIED: Logic to rename/move the WAV file ***
                                    val oldPath = result.wavFilePath
                                    val newFilePath = moveWavFile(context, oldPath, userId, result.modifiedText)
                                    if (newFilePath != null) {
                                        // Update the item in the list with the new path
                                        val updatedItem = result.copy(wavFilePath = newFilePath)
                                        resultList[index] = updatedItem
                                        Log.i(TAG, "WAV file moved to $newFilePath")
                                    } else {
                                        Log.w(TAG, "Could not move WAV file for text: ${result.modifiedText}")
                                    }

                                    val utteranceId = UUID.randomUUID().toString()
                                    tts?.speak(
                                        result.modifiedText,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        utteranceId
                                    )
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
                                enabled = !isStarted && !isTtsSpeaking && (!isPlaying || currentlyPlayingPath == result.wavFilePath)
                            ) {
                                Icon(
                                    imageVector = if (currentlyPlayingPath == result.wavFilePath) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (currentlyPlayingPath == result.wavFilePath) "Stop" else "Play"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * *** ADDED: Helper function to move the WAV file to a new directory based on modified text. ***
 */
private fun moveWavFile(context: Context, oldPath: String, userId: String, newText: String): String? {
    val oldFile = File(oldPath)
    if (!oldFile.exists()) {
        Log.e(TAG, "Original WAV file not found at: $oldPath")
        return null
    }

    val newDir = File(context.filesDir, "wavs/$userId/$newText")

    if (!newDir.exists()) {
        if (!newDir.mkdirs()) {
            Log.e(TAG, "Failed to create new directory: ${newDir.absolutePath}")
            return null
        }
    }

    val newFile = File(newDir, oldFile.name)

    // If the file is already in the correct location, do nothing.
    if (oldFile.absolutePath == newFile.absolutePath) {
        return oldFile.absolutePath
    }

    return try {
        if (oldFile.renameTo(newFile)) {
            // Delete the old parent directory if it's empty
            val oldParentDir = oldFile.parentFile
            if (oldParentDir != null && oldParentDir.isDirectory && oldParentDir.listFiles()?.isEmpty() == true) {
                oldParentDir.delete()
            }
            newFile.absolutePath
        } else {
            Log.e(TAG, "Failed to rename file from $oldPath to ${newFile.absolutePath}")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error moving file", e)
        null
    }
}


/**
 * Reads a WAV file and returns its audio data as a FloatArray.
 * Note: This makes a simplifying assumption that the WAV file is 16-bit PCM.
 *
 * @param path The absolute path to the WAV file.
 * @return A FloatArray containing the audio samples normalized to [-1, 1], or null on error.
 */
private fun readWavFileToFloatArray(path: String): FloatArray? {
    try {
        val file = File(path)
        val fileInputStream = FileInputStream(file)
        val byteBuffer = fileInputStream.readBytes()
        fileInputStream.close()

        // The first 44 bytes of a standard WAV file are the header. We skip it.
        val headerSize = 44
        if (byteBuffer.size <= headerSize) {
            Log.e(TAG, "WAV file is too small to contain audio data: ${file.name}")
            return null
        }

        // We assume the audio data is 16-bit PCM, little-endian.
        val shortBuffer = ByteBuffer.wrap(byteBuffer, headerSize, byteBuffer.size - headerSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val numSamples = shortBuffer.remaining()
        val floatArray = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            // Convert 16-bit short to float in the range [-1.0, 1.0]
            floatArray[i] = shortBuffer.get(i) / 32768.0f
        }
        return floatArray
    } catch (e: Exception) {
        Log.e(TAG, "Error reading WAV file: $path", e)
        return null
    }
}

@Composable
private fun EditRecognitionDialog(
    originalText: String,
    currentText: String,
    wavFilePath: String, // We need the path to the audio file
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentText) }
    var newRecognitionResult by remember { mutableStateOf<String?>(null) }
    var isRecognizing by remember { mutableStateOf(false) }
    var recognitionError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Use the non-streaming (offline) recognizer for whole-file recognition
    val recognizer: OfflineRecognizer = remember { SimulateStreamingAsr.recognizer }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Recognition") },
        text = {
            Column {
                Text("Original:", fontWeight = FontWeight.Bold)
                Text(originalText)
                Spacer(modifier = Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Modified Text") }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // "Recognize again" functionality
                if (wavFilePath.isNotEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    isRecognizing = true
                                    newRecognitionResult = null
                                    recognitionError = null
                                    try {
                                        val audioData = readWavFileToFloatArray(wavFilePath)
                                        if (audioData != null) {
                                            val stream = recognizer.createStream()
                                            stream.acceptWaveform(
                                                audioData,
                                                sampleRateInHz
                                            )
                                            recognizer.decode(stream)
                                            val result = recognizer.getResult(stream).text
                                            launch(Dispatchers.Main) {
                                                newRecognitionResult = result
                                            }
                                            stream.release()
                                        } else {
                                            launch(Dispatchers.Main) {
                                                recognitionError =
                                                    "Error: Could not read audio file."
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Re-recognition failed", e)
                                        launch(Dispatchers.Main) {
                                            recognitionError = "Error: Recognition failed."
                                        }
                                    } finally {
                                        launch(Dispatchers.Main) {
                                            isRecognizing = false
                                        }
                                    }
                                }
                            },
                            enabled = !isRecognizing
                        ) {
                            Text(text = "Recognize again")
                        }

                        if (isRecognizing) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                        }

                        recognitionError?.let { error ->
                            Text(error, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
                        }

                        newRecognitionResult?.let { result ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("New Result:", fontWeight = FontWeight.Bold)
                            Text(result)
                            TextButton(onClick = {
                                text = result
                                newRecognitionResult = null // Clear after applying
                            }) {
                                Text("Replace with new result")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
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
