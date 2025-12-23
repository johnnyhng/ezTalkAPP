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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.SimulateStreamingAsr
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.utils.MediaController
import tw.com.johnnyhng.eztalk.asr.utils.getRemoteCandidates
import tw.com.johnnyhng.eztalk.asr.utils.readWavFileToFloatArray
import tw.com.johnnyhng.eztalk.asr.utils.saveAsWav
import tw.com.johnnyhng.eztalk.asr.utils.saveJsonl
import tw.com.johnnyhng.eztalk.asr.widgets.WaveformDisplay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

private var audioRecord: AudioRecord? = null
private const val sampleRateInHz = 16000

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
    var textInput by remember { mutableStateOf("") }
    var currentTranscript by remember { mutableStateOf<Transcript?>(null) }

    // Candidate states
    var localCandidate by remember { mutableStateOf<String?>(null) }
    var remoteCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingCandidates by remember { mutableStateOf(false) }

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

    // Initialize recognizer in the background
    LaunchedEffect(key1 = homeViewModel.selectedModel) {
        isAsrModelLoading = true
        Log.i(TAG, "ASR model initialization started.")
        withContext(IO) {
            SimulateStreamingAsr.initOfflineRecognizer(
                context.assets,
                homeViewModel.selectedModel
            )
        }
        isAsrModelLoading = false
        Log.i(TAG, "ASR model initialization finished.")
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

    // Fetch candidates when a new transcript is available
    LaunchedEffect(currentTranscript) {
        val transcript = currentTranscript
        if (transcript != null && transcript.wavFilePath.isNotEmpty()) {
            try {
                isFetchingCandidates = true
                localCandidate = null
                remoteCandidates = emptyList()

                coroutineScope {
                    // Local re-recognition
                    launch(IO) {
                        try {
                            val audioData = readWavFileToFloatArray(transcript.wavFilePath)
                            if (audioData != null) {
                                val recognizer = SimulateStreamingAsr.recognizer
                                val stream = recognizer.createStream()
                                stream.acceptWaveform(audioData, sampleRateInHz)
                                recognizer.decode(stream)
                                val localResultText = recognizer.getResult(stream).text
                                stream.release()
                                withContext(Main) {
                                    localCandidate = localResultText
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Local re-recognition failed", e)
                        }
                    }

                    // Remote recognition
                    if (userSettings.recognitionUrl.isNotBlank()) {
                        launch(IO) {
                            val sentences = getRemoteCandidates(
                                context = context,
                                wavFilePath = transcript.wavFilePath,
                                userId = userSettings.userId,
                                recognitionUrl = userSettings.recognitionUrl,
                                originalText = transcript.recognizedText,
                                currentText = transcript.modifiedText
                            )
                            withContext(Main) {
                                remoteCandidates = sentences
                            }
                        }
                    }
                }
            } finally {
                isFetchingCandidates = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAudio()
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
                CoroutineScope(IO).launch {
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
                            launch(Main) {
                                latestAudioSamples = samples
                            }
                        }
                    }
                    samplesChannel.close() // Close the channel to signal the end
                    Log.i(TAG, "Audio recording stopped")
                }

                // --- Coroutine to process audio ---
                CoroutineScope(Default).launch {
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
                        withContext(Main) { isRecognizingSpeech = true }

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

                                    withContext(Main) {
                                        currentTranscript = newTranscript
                                        textInput = newTranscript.modifiedText

                                        if (wavPath != null) {
                                            saveJsonl(
                                                context = context,
                                                userId = userId,
                                                filename = filename,
                                                originalText = newTranscript.recognizedText,
                                                modifiedText = newTranscript.modifiedText,
                                                checked = false
                                            )
                                        }
                                    }
                                }
                            } finally {
                                stream.release()
                            }
                        }
                        withContext(Main) { isRecognizingSpeech = false }
                    }
                }
            }
        } else {
            stopAudio()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            label = { Text("Recognized Text") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        HomeButtonRow(
            modifier = Modifier.padding(vertical = 16.dp),
            isStarted = isStarted,
            onRecordingButtonClick = onRecordingButtonClick,
            onCopyButtonClick = {
                if (textInput.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(textInput))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Nothing to copy", Toast.LENGTH_SHORT).show()
                }
            },
            onClearButtonClick = {
                textInput = ""
                currentTranscript = null
                localCandidate = null
                remoteCandidates = emptyList()
            },
            onTtsButtonClick = {
                val utteranceId = UUID.randomUUID().toString()
                tts?.speak(textInput, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

                currentTranscript?.let { transcript ->
                    val updatedTranscript = transcript.copy(modifiedText = textInput, checked = true)
                    currentTranscript = updatedTranscript

                    val file = File(transcript.wavFilePath)
                    val filename = file.nameWithoutExtension

                    coroutineScope.launch(IO) {
                        saveJsonl(
                            context = context,
                            userId = userId,
                            filename = filename,
                            originalText = updatedTranscript.recognizedText,
                            modifiedText = updatedTranscript.modifiedText,
                            checked = updatedTranscript.checked
                        )
                    }
                }
            },
            onPlaybackButtonClick = {
                currentTranscript?.wavFilePath?.let { path ->
                    if (currentlyPlaying == path) {
                        MediaController.stop()
                    } else {
                        MediaController.play(path)
                    }
                }
            },
            isTtsButtonEnabled = !isTtsSpeaking && textInput.isNotEmpty() && currentTranscript != null,
            isPlaybackButtonEnabled = !isStarted && currentTranscript?.wavFilePath?.isNotEmpty() == true,
            isPlaying = currentlyPlaying == currentTranscript?.wavFilePath,
            isPlaybackActive = currentlyPlaying != null || isTtsSpeaking,
            isAsrModelLoading = isAsrModelLoading
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

        val candidates = remember(currentTranscript, localCandidate, remoteCandidates) {
            (listOfNotNull(
                currentTranscript?.recognizedText,
                localCandidate
            ) + remoteCandidates).distinct().filter { it.isNotBlank() }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(candidates) { index, candidate ->
                Text(
                    text = "${index + 1}: $candidate",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { textInput = candidate }
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp
                )
            }
        }

        if (isRecognizingSpeech || isFetchingCandidates) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
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
    onTtsButtonClick: () -> Unit,
    onPlaybackButtonClick: () -> Unit,
    isPlaybackActive: Boolean,
    isTtsButtonEnabled: Boolean,
    isPlaybackButtonEnabled: Boolean,
    isPlaying: Boolean,
    isAsrModelLoading: Boolean,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPlaybackButtonClick,
            enabled = isPlaybackButtonEnabled
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Play",
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(
            onClick = onTtsButtonClick,
            enabled = isTtsButtonEnabled
        ) {
            Icon(
                imageVector = Icons.Default.RecordVoiceOver,
                contentDescription = "Speak Text",
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(
            onClick = onRecordingButtonClick,
            enabled = !isPlaybackActive && !isAsrModelLoading
        ) {
            Icon(
                imageVector = if (isStarted) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = stringResource(if (isStarted) R.string.stop else R.string.start),
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(
            onClick = onCopyButtonClick,
            enabled = !isStarted && !isPlaybackActive
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = stringResource(id = R.string.copy),
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(
            onClick = onClearButtonClick,
            enabled = !isStarted && !isPlaybackActive
        ) {
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = stringResource(id = R.string.clear),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
