package tw.com.johnnyhng.eztalk.asr.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.SimulateStreamingAsr
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.data.classes.QueueState
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.utils.*
import tw.com.johnnyhng.eztalk.asr.widgets.EditRecognitionDialog
import tw.com.johnnyhng.eztalk.asr.widgets.EditableDropdown
import tw.com.johnnyhng.eztalk.asr.widgets.WaveformDisplay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

private var audioRecord: AudioRecord? = null
private const val sampleRateInHz = 16000

// This dynamic array will keep records for future feedback implementation.
private val feedbackRecords = mutableListOf<Transcript>()

@Composable
fun HomeScreen(
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

    // Waveform and countdown states
    var latestAudioSamples by remember { mutableStateOf(FloatArray(0)) }
    var isRecognizingSpeech by remember { mutableStateOf(false) }
    var countdownProgress by remember { mutableFloatStateOf(0f) }

    // Data collect mode state
    var isDataCollectMode by remember { mutableStateOf(false) }
    var dataCollectText by remember { mutableStateOf("") }

    // Sequence mode state
    var isSequenceMode by rememberSaveable { mutableStateOf(false) }
    val textQueue = rememberSaveable(saver = listSaver(
        save = { it.toList() },
        restore = { it.toMutableStateList() }
    )) { mutableStateListOf<String>() }
    var lastUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNoQueueMessage by remember { mutableStateOf(false) }


    // Collect settings from the ViewModel
    val userSettings by homeViewModel.userSettings.collectAsState()
    val userId = userSettings.userId

    // Effect to clear queue when user changes
    LaunchedEffect(userId) {
        if (lastUserId != null && lastUserId != userId) {
            saveQueueState(context, lastUserId!!, QueueState(dataCollectText, textQueue.toList()))
            textQueue.clear()
            isSequenceMode = false
            dataCollectText = ""
        }
        lastUserId = userId
    }

    // Effect to reset sequence mode when data collect mode is off
    LaunchedEffect(isDataCollectMode) {
        if (isDataCollectMode) {
            restoreQueueState(context, userId)?.let {
                textQueue.addAll(it.queue)
                dataCollectText = it.currentText
                isSequenceMode = textQueue.isNotEmpty()
            }
        } else {
            isSequenceMode = false
        }
    }


    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val lines =
                            inputStream.bufferedReader().readLines().filter { it.isNotBlank() }
                        withContext(Dispatchers.Main) {
                            textQueue.clear()
                            if (lines.isNotEmpty()) {
                                dataCollectText = lines.first()
                                textQueue.addAll(lines.drop(1))
                                isSequenceMode = true
                                showNoQueueMessage = false
                                saveQueueState(context, userId, QueueState(dataCollectText, textQueue.toList()))
                            } else {
                                dataCollectText = ""
                                isSequenceMode = false
                                deleteQueueState(context, userId)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading file: ", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error reading file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


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
                if (userSettings.recognitionUrl.isBlank() || isDataCollectMode) continue

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
                @OptIn(DelicateCoroutinesApi::class)
                CoroutineScope(Dispatchers.Default).launch {
                    val lingerMs = userSettings.lingerMs
                    val partialIntervalMs = userSettings.partialIntervalMs
                    val saveVadSegmentsOnly = userSettings.saveVadSegmentsOnly

                    var buffer = arrayListOf<Float>()
                    val reserveForPreviousSpeechDetectedMs = 500
                    val keep = (sampleRateInHz / 1000) * reserveForPreviousSpeechDetectedMs
                    var offset = 0
                    val windowSize = 512
                    var startOffset = 0
                    var lastSpeechDetectedOffset = 0
                    var isSpeechStarted = false
                    var startTime = System.currentTimeMillis()
                    var lastText: String
                    var added = false

                    val utteranceSegments = mutableListOf<FloatArray>()
                    var lastVadPacketAt = 0L

                    val fullRecordingBuffer = arrayListOf<Float>()

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
                                    startOffset = max(0, offset - windowSize - keep)
                                }
                            }
                        }

                        if (isSpeechStarted) {
                            val elapsed = System.currentTimeMillis() - startTime
                            if (elapsed > partialIntervalMs) {
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
                            if (!saveVadSegmentsOnly) {
                                lastSpeechDetectedOffset = offset
                            }
                            val seg = SimulateStreamingAsr.vad.front().samples
                            SimulateStreamingAsr.vad.pop()
                            utteranceSegments.add(seg)
                            lastVadPacketAt = System.currentTimeMillis()
                        }

                        if (utteranceSegments.isNotEmpty()) {
                            val since = System.currentTimeMillis() - lastVadPacketAt
                            val progress = (since.toFloat() / lingerMs).coerceIn(0f, 1f)
                            launch(Dispatchers.Main) { countdownProgress = progress }
                            // Process if silence duration is met OR if it's the final flush
                            if (since >= lingerMs || (done && utteranceSegments.isNotEmpty()) || !isStarted) {
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
                                    lastSpeechDetectedOffset = min(
                                        buffer.size - 1,
                                        lastSpeechDetectedOffset + keep
                                    )
                                    fullRecordingBuffer.clear()
                                    fullRecordingBuffer.addAll(
                                        buffer.subList(
                                            startOffset,
                                            lastSpeechDetectedOffset
                                        )
                                    )
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

                                    val originalText = result.text
                                    val modifiedText =
                                        if (isDataCollectMode) dataCollectText else result.text

                                    val timestamp = SimpleDateFormat(
                                        "yyyyMMdd-HHmmss",
                                        Locale.getDefault()
                                    ).format(
                                        Date()
                                    )
                                    val filename = "${timestamp}.app"

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
                                            originalText = originalText,
                                            modifiedText = modifiedText, // Initially, modified is same as original
                                            checked = isDataCollectMode
                                        )

                                        // Enqueue for background recognition
                                        if (userSettings.recognitionUrl.isNotBlank() && !isDataCollectMode) {
                                            recognitionQueue.trySend(wavPath)
                                        }
                                    }

                                    val newTranscript = Transcript(
                                        recognizedText = originalText,
                                        wavFilePath = wavPath ?: "",
                                        modifiedText = modifiedText,
                                        checked = isDataCollectMode,
                                        canCheck = !isDataCollectMode
                                    )

                                    if (modifiedText.isNotBlank()) {
                                        if (added && resultList.isNotEmpty()) {
                                            resultList[resultList.size - 1] = newTranscript
                                        } else {
                                            resultList.add(newTranscript)
                                        }
                                    }

                                    // Add the completed record to our feedback list
                                    if (modifiedText.isNotBlank() && resultList.isNotEmpty()) {
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
                                startOffset = 0
                                lastSpeechDetectedOffset = 0
                                added = false
                                launch(Dispatchers.Main) {
                                    isRecognizingSpeech = false
                                    countdownProgress = 0f
                                }
                            }
                        } else {
                            if (isRecognizingSpeech) {
                                val sinceLastPacket =
                                    System.currentTimeMillis() - lastVadPacketAt
                                if (lastVadPacketAt != 0L && sinceLastPacket > lingerMs) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text("Data Collect Mode")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isDataCollectMode,
                    onCheckedChange = { isDataCollectMode = it }
                )
            }
            if (isDataCollectMode) {
                DataCollectWidget(
                    text = dataCollectText,
                    onTextChange = { dataCollectText = it },
                    onTtsClick = {
                        if (dataCollectText.isNotBlank()) {
                            tts?.speak(
                                dataCollectText,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                UUID.randomUUID().toString()
                            )
                        }
                    },
                    isSequenceMode = isSequenceMode,
                    onSequenceModeChange = { newMode ->
                        if (newMode) {
                            val restoredState = restoreQueueState(context, userId)
                            if (restoredState != null && (restoredState.currentText.isNotBlank() || restoredState.queue.isNotEmpty())) {
                                textQueue.clear()
                                textQueue.addAll(restoredState.queue)
                                dataCollectText = restoredState.currentText
                                isSequenceMode = true
                                showNoQueueMessage = false
                            } else {
                                showNoQueueMessage = true
                            }
                        } else {
                            isSequenceMode = false
                            saveQueueState(context, userId, QueueState(dataCollectText, textQueue.toList()))
                        }
                    },
                    onUploadClick = {
                        filePickerLauncher.launch("text/plain")
                    },
                    onNextClick = {
                        if (textQueue.isNotEmpty()) {
                            dataCollectText = textQueue.removeFirst()
                            saveQueueState(context, userId, QueueState(dataCollectText, textQueue.toList()))
                        } else {
                            // This was the last item, turn off sequence mode
                            isSequenceMode = false
                            dataCollectText = ""
                            deleteQueueState(context, userId)
                        }
                    },
                    isNextEnabled = isSequenceMode,
                    isSequenceModeSwitchEnabled = true, // Always allow turning on/off
                    showNoQueueMessage = showNoQueueMessage
                )
            }
            HomeButtonRow(
                modifier = Modifier.padding(vertical = 16.dp),
                isStarted = isStarted,
                onRecordingButtonClick = onRecordingButtonClick,
                onCopyButtonClick = {
                    if (resultList.isNotEmpty()) {
                        val s =
                            resultList.mapIndexed { i, result -> "${i + 1}: ${result.modifiedText}" }
                                .joinToString(separator = "")
                        clipboardManager.setText(AnnotatedString(s))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT)
                            .show()
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
                isEditing = isEditing,
                isDataCollectMode = isDataCollectMode,
                dataCollectText = dataCollectText
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
                                .clickable(enabled = !isRecognizingSpeech && !isEditing && !isStarted && result.canCheck) {
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

                                // Delete Button
                                IconButton(
                                    onClick = {
                                        if (result.wavFilePath.isNotEmpty()) {
                                            val deleted = deleteTranscriptFiles(result.wavFilePath)
                                            if (deleted) {
                                                // Remove from resultList
                                                val removedTranscript = resultList.removeAt(index)
                                                // Also remove from feedbackRecords
                                                feedbackRecords.remove(removedTranscript)
                                                Toast.makeText(context, R.string.file_deleted_successfully, Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, R.string.file_deletion_failed, Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, R.string.no_file_to_delete, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isStarted && !isTtsSpeaking && currentlyPlaying == null && !isEditing
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete"
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
    isDataCollectMode: Boolean,
    dataCollectText: String,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = onRecordingButtonClick,
            enabled = !isPlaybackActive && !isAsrModelLoading && !isEditing && (!isDataCollectMode || dataCollectText.isNotBlank())
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
