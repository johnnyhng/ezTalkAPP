package tw.com.johnnyhng.eztalk.asr.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.SimulateStreamingAsr
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.audio.AudioIOManager
import tw.com.johnnyhng.eztalk.asr.audio.AudioInputRoutingSession
import tw.com.johnnyhng.eztalk.asr.audio.NoopAudioInputRoutingSession
import tw.com.johnnyhng.eztalk.asr.audio.rememberSpeechOutputController
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.utterance.AsrUtteranceVariantBuffer
import tw.com.johnnyhng.eztalk.asr.utils.MediaController
import tw.com.johnnyhng.eztalk.asr.utils.feedbackToBackend
import tw.com.johnnyhng.eztalk.asr.utils.loadTranslateCandidates
import tw.com.johnnyhng.eztalk.asr.utils.optStringList
import tw.com.johnnyhng.eztalk.asr.utils.readWavFileToFloatArray
import tw.com.johnnyhng.eztalk.asr.utils.saveAsWav
import tw.com.johnnyhng.eztalk.asr.utils.persistTranscriptJsonl
import tw.com.johnnyhng.eztalk.asr.utils.syncTranscriptCandidatesFromJsonl
import tw.com.johnnyhng.eztalk.asr.workflow.applyTranslateFeedbackResult
import tw.com.johnnyhng.eztalk.asr.workflow.appendTranslateSamples
import tw.com.johnnyhng.eztalk.asr.workflow.buildTranslateFinalAudio
import tw.com.johnnyhng.eztalk.asr.workflow.createTranslateTranscript
import tw.com.johnnyhng.eztalk.asr.workflow.markTranslateVadSegmentDetected
import tw.com.johnnyhng.eztalk.asr.workflow.reduceTranscriptAfterConfirmation
import tw.com.johnnyhng.eztalk.asr.workflow.shouldCompleteTranslateCapture
import tw.com.johnnyhng.eztalk.asr.workflow.submitTranslateFeedback
import tw.com.johnnyhng.eztalk.asr.workflow.TranslateFeedbackSubmission
import tw.com.johnnyhng.eztalk.asr.workflow.TranslateCaptureState
import tw.com.johnnyhng.eztalk.asr.widgets.WaveformDisplay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private var audioRecord: AudioRecord? = null
private var audioInputRoutingSession: AudioInputRoutingSession = NoopAudioInputRoutingSession
private const val sampleRateInHz = 16000

private data class TranslateUiState(
    val textInput: String = "",
    val transcript: Transcript? = null,
    val localCandidate: String? = null,
    val remoteCandidates: List<String> = emptyList(),
    val latestAudioSamples: FloatArray = FloatArray(0),
    val isRecognizingSpeech: Boolean = false,
    val isFetchingCandidates: Boolean = false
) {
    val isMutable: Boolean
        get() = transcript?.mutable != false

    val candidates: List<String>
        get() = (listOfNotNull(transcript?.recognizedText, localCandidate) +
            transcript.orEmptyLocalCandidates() +
            remoteCandidates)
            .distinct()
            .filter { it.isNotBlank() }
}

private fun logTranslateJsonlUpdate(reason: String, transcript: Transcript) {
    Log.d(
        TAG,
        "Translate jsonl update: reason=$reason, file=${File(transcript.wavFilePath).name}, modified=${transcript.modifiedText}, checked=${transcript.checked}, mutable=${transcript.mutable}, localCandidates=${transcript.localCandidates.size}, remoteCandidates=${transcript.remoteCandidates.size}"
    )
}

@Composable
fun TranslateScreen(
    homeViewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val activity = LocalContext.current as Activity
    val coroutineScope = rememberCoroutineScope()
    val audioIOManager = remember(context) { AudioIOManager(context.applicationContext) }

    // UI state
    var isStarted by remember { mutableStateOf(false) }
    var uiState by remember { mutableStateOf(TranslateUiState()) }
    var fetchJob by remember { mutableStateOf<Job?>(null) }

    // Collect settings from the ViewModel
    val userSettings by homeViewModel.userSettings.collectAsState()
    val selectedModel by homeViewModel.selectedModelFlow.collectAsState()
    val isAsrModelLoading by homeViewModel.isAsrModelLoading.collectAsState()
    val userId = userSettings.userId

    // Playback state
    val currentlyPlaying by MediaController.currentlyPlaying.collectAsState()

    // TTS state
    val (speechController, speechState) = rememberSpeechOutputController(
        preferredLocale = Locale.TRADITIONAL_CHINESE,
        preferredOutputDeviceId = userSettings.preferredAudioOutputDeviceId
    )
    val isTtsSpeaking = speechState.isSpeaking

    // Channel to signal the audio processor to flush remaining buffers
    val flushChannel = remember { Channel<Unit>(Channel.CONFLATED) }
    val utteranceVariantBuffer = remember { AsrUtteranceVariantBuffer() }

    // Initialize recognizer in the background
    LaunchedEffect(selectedModel?.name, userSettings.userId, userSettings.mobileModelSha256) {
        if (selectedModel != null) {
            Log.i(TAG, "ASR model initialization started.")
            homeViewModel.ensureSelectedModelInitialized()
            Log.i(TAG, "ASR model initialization finished.")
        }
    }

    // Fetch candidates when a new transcript is available
    LaunchedEffect(uiState.transcript?.wavFilePath) {
        fetchJob = coroutineContext[Job] // Capture the job of this effect
        val transcript = uiState.transcript
        if (transcript != null && transcript.wavFilePath.isNotEmpty()) {
            try {
                uiState = uiState.copy(
                    isFetchingCandidates = true,
                    localCandidate = transcript.localCandidates.firstOrNull(),
                    remoteCandidates = emptyList()
                )

                val loaded = loadTranslateCandidates(
                    context = context,
                    userId = userSettings.userId,
                    transcript = transcript,
                    recognitionUrl = userSettings.effectiveRecognitionUrl,
                    allowInsecureTls = userSettings.allowInsecureTls,
                    audioReader = ::readWavFileToFloatArray,
                    recognizerBlock = { audioData ->
                        val recognizer = SimulateStreamingAsr.recognizer
                        val stream = recognizer.createStream()
                        try {
                            stream.acceptWaveform(audioData, sampleRateInHz)
                            recognizer.decode(stream)
                            recognizer.getResult(stream).text
                        } finally {
                            stream.release()
                        }
                    }
                )
                if (loaded.transcript.localCandidates != transcript.localCandidates) {
                    logTranslateJsonlUpdate("local_rerecognition", loaded.transcript)
                }
                uiState = uiState.copy(
                    transcript = loaded.transcript,
                    localCandidate = loaded.localCandidate,
                    remoteCandidates = loaded.remoteCandidates
                )
            } finally {
                uiState = uiState.copy(isFetchingCandidates = false)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAudio()
            speechController.stop()
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

                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val managedRecord = audioIOManager.createMicAudioRecord(
                    sampleRateInHz = sampleRateInHz,
                    channelConfig = channelConfig,
                    audioFormat = audioFormat,
                    preferredInputDeviceId = userSettings.preferredAudioInputDeviceId
                )
                audioRecord = managedRecord.audioRecord
                audioInputRoutingSession = managedRecord.routingSession
                managedRecord.routingMessage?.let { Log.i(TAG, it) }
                audioIOManager.logMicRoutingPreparation(
                    preferredInputDeviceId = userSettings.preferredAudioInputDeviceId,
                    routingMessage = managedRecord.routingMessage
                )
                val readLogger = audioIOManager.createAudioInputReadLogger("TranslateScreen")
                homeViewModel.reportAudioInputRoutingState(managedRecord.routingMessage)
                if (audioRecord == null) {
                    audioInputRoutingSession.release()
                    audioInputRoutingSession = NoopAudioInputRoutingSession
                    Log.e(TAG, "Translate microphone recorder initialization failed")
                    isStarted = false
                    return@LaunchedEffect
                }
                SimulateStreamingAsr.resetVadSafely()

                // --- Coroutine to record audio ---
                CoroutineScope(IO).launch {
                    Log.i(TAG, "Audio recording started")
                    val interval = 0.1 // 100ms
                    val bufferSize = (interval * sampleRateInHz).toInt()
                    val buffer = ShortArray(bufferSize)

                    audioRecord?.startRecording()
                    val activeInputLabel = audioIOManager.logMicRoutingActivation(
                        audioRecord = audioRecord,
                        preferredInputDeviceId = userSettings.preferredAudioInputDeviceId,
                        sampleRateInHz = sampleRateInHz,
                        bufferSize = bufferSize,
                        audioSource = managedRecord.audioSource
                    )
                    homeViewModel.reportAudioInputRoutingState(
                        managedRecord.routingMessage,
                        activeInputLabel
                    )
                    while (isStarted) {
                        if (currentlyPlaying != null || isTtsSpeaking) { 
                            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                Log.d(TAG, "Translate: Stopping AudioRecord hardware to release SCO resources")
                                audioRecord?.stop()
                            }
                            delay(100)
                            continue
                        }
                        
                        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_STOPPED) {
                            Log.d(TAG, "Translate: Restarting AudioRecord hardware after playback")
                            audioRecord?.startRecording()
                        }

                        val ret = audioRecord?.read(buffer, 0, buffer.size)
                        readLogger.onRead(ret ?: -1, buffer, activeInputLabel)
                        if (ret != null && ret > 0) {
                            val samples = FloatArray(ret) { i -> buffer[i] / 32768.0f }
                            samplesChannel.send(samples)
                            launch(Main) {
                                uiState = uiState.copy(latestAudioSamples = samples)
                            }
                        }
                    }
                    samplesChannel.close() // Close the channel to signal the end
                    Log.i(TAG, "Audio recording stopped")
                }

                // --- Coroutine to process audio ---
                CoroutineScope(Default).launch {
                    val reserveForPreviousSpeechDetectedMs = 500
                    val keep = (sampleRateInHz / 1000) * reserveForPreviousSpeechDetectedMs
                    val captureState = TranslateCaptureState()
                    val realtimeRecognitionInterval = 500L // ms

                    // Reset UI for new recording
                    withContext(Main) {
                        utteranceVariantBuffer.reset()
                        uiState = uiState.copy(
                            textInput = "",
                            transcript = null,
                            localCandidate = null,
                            remoteCandidates = emptyList()
                        )
                    }

                    var done = false
                    while (!done) {
                        val s = samplesChannel.tryReceive().getOrNull()
                        val flushRequest = flushChannel.tryReceive().getOrNull()

                        if (s != null) {
                            SimulateStreamingAsr.acceptVadWaveformSafely(s)
                            appendTranslateSamples(
                                state = captureState,
                                samples = s,
                                keepSamples = keep,
                                speechDetected = SimulateStreamingAsr.isVadSpeechDetectedSafely()
                            )

                            if (captureState.isSpeechStarted) {
                                val now = System.currentTimeMillis()
                                if (now - captureState.lastRealtimeRecognitionTime > realtimeRecognitionInterval) {
                                    captureState.lastRealtimeRecognitionTime = now
                                    val audioForRealtime = captureState.fullRecordingBuffer
                                        .subList(
                                            captureState.speechStartOffset,
                                            captureState.fullRecordingBuffer.size
                                        )
                                        .toFloatArray()
                                    
                                    // Fire-and-forget recognition job
                                    launch(IO) {
                                        val stream = SimulateStreamingAsr.recognizer.createStream()
                                        try {
                                            stream.acceptWaveform(audioForRealtime, sampleRateInHz)
                                            SimulateStreamingAsr.recognizer.decode(stream)
                                            val result = SimulateStreamingAsr.recognizer.getResult(stream)
                                            utteranceVariantBuffer.add(result.text)
                                            if (isStarted) { // check user hasn't stopped
                                                withContext(Main) {
                                                    uiState = uiState.copy(textInput = result.text)
                                                }
                                            }
                                        } finally {
                                            stream.release() 
                                        }
                                    }
                                }
                            }
                        }

                        while (true) {
                            if (SimulateStreamingAsr.popVadSegmentSafely() == null) break
                            markTranslateVadSegmentDetected(captureState)
                            // Segment content is not used in this screen; draining queue updates VAD state.
                        }

                        if (shouldCompleteTranslateCapture(
                                hasSample = s != null,
                                flushRequested = flushRequest != null,
                                samplesChannelClosed = samplesChannel.isClosedForReceive
                            )) {
                            done = true
                        } else if (s == null) {
                            delay(10)
                        }
                    }

                    // Final recognition and save
                    if (captureState.isSpeechStarted && captureState.speechStartOffset != -1) {
                        withContext(Main) {
                            uiState = uiState.copy(isRecognizingSpeech = true)
                        }

                        val audioToRecognize = buildTranslateFinalAudio(
                            state = captureState,
                            keepSamples = keep
                        )

                        if (audioToRecognize.isNotEmpty()) {
                            val stream = SimulateStreamingAsr.recognizer.createStream()
                            try {
                                stream.acceptWaveform(audioToRecognize, sampleRateInHz)
                                SimulateStreamingAsr.recognizer.decode(stream)
                                val result = SimulateStreamingAsr.recognizer.getResult(stream)
                                utteranceVariantBuffer.add(result.text)

                                if (result.text.isNotBlank()) {
                                    val utteranceBundle = utteranceVariantBuffer.build(version = 0)
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

                                    val newTranscript = createTranslateTranscript(
                                        recognizedText = result.text,
                                        wavFilePath = wavPath ?: "",
                                        utteranceVariants = utteranceBundle?.variants ?: listOf(result.text)
                                    )

                                    withContext(Main) {
                                        uiState = uiState.copy(
                                            transcript = newTranscript,
                                            textInput = newTranscript.modifiedText,
                                            localCandidate = newTranscript.localCandidates.firstOrNull(),
                                            remoteCandidates = newTranscript.remoteCandidates
                                        )

                                        if (wavPath != null) {
                                            logTranslateJsonlUpdate("final_recognition", newTranscript)
                                            persistTranscriptJsonl(context, userId, newTranscript)
                                        }
                                    }
                                }
                            } finally {
                                stream.release()
                                utteranceVariantBuffer.reset()
                            }
                        }
                        withContext(Main) {
                            uiState = uiState.copy(isRecognizingSpeech = false)
                        }
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
        val isMutable = uiState.isMutable
        
        if (isMutable) {
            OutlinedTextField(
                value = uiState.textInput,
                onValueChange = { uiState = uiState.copy(textInput = it) },
                label = { Text(stringResource(id = R.string.recognized_text)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                textStyle = TextStyle(fontSize = 20.sp, textAlign = TextAlign.Center)
            )
        } else {
            // TextView style replacement for read-only state
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = uiState.textInput,
                    modifier = Modifier.padding(24.dp),
                    style = TextStyle(
                        fontSize = 24.sp, 
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        HomeButtonRow(
            modifier = Modifier.padding(vertical = 16.dp),
            isStarted = isStarted,
            onRecordingButtonClick = onRecordingButtonClick,
            onCopyButtonClick = {
                if (uiState.textInput.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(uiState.textInput))
                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.nothing_to_copy, Toast.LENGTH_SHORT).show()
                }
            },
            onClearButtonClick = {
                uiState = uiState.copy(
                    textInput = "",
                    transcript = null,
                    localCandidate = null,
                    remoteCandidates = emptyList()
                )
            },
            onTtsButtonClick = {
                speechController.speak(uiState.textInput)

                uiState.transcript?.let { transcript ->
                    coroutineScope.launch {
                        when (
                            val result = submitTranslateFeedback(
                                transcript = transcript,
                                newText = uiState.textInput,
                                enableTtsFeedback = userSettings.enableTtsFeedback,
                                remoteCandidates = uiState.remoteCandidates,
                                fetchJob = fetchJob,
                                feedbackBlock = { currentTranscriptForFeedback ->
                                    withContext(IO) {
                                        feedbackToBackend(
                                            userSettings.backendUrl,
                                            currentTranscriptForFeedback.wavFilePath,
                                            userSettings.userId,
                                            allowInsecureTls = userSettings.allowInsecureTls
                                        )
                                    }
                                }
                            )
                        ) {
                            is TranslateFeedbackSubmission.Success -> {
                                uiState = uiState.copy(
                                    transcript = result.transcript,
                                    textInput = result.transcript.modifiedText,
                                    localCandidate = result.transcript.localCandidates.firstOrNull(),
                                    remoteCandidates = result.transcript.remoteCandidates
                                )
                                withContext(IO) {
                                    val reason = if (result.sentBackendFeedback) "tts_feedback_success" else "tts_without_feedback"
                                    logTranslateJsonlUpdate(reason, result.transcript)
                                    persistTranscriptJsonl(context, userId, result.transcript)
                                }
                            }

                            TranslateFeedbackSubmission.Failed -> {
                                withContext(Main) {
                                    Toast.makeText(context, R.string.feedback_failed, Toast.LENGTH_SHORT).show()
                                }
                            }

                            TranslateFeedbackSubmission.Skipped -> Unit
                        }
                    }
                }
            },
            onPlaybackButtonClick = {
                uiState.transcript?.wavFilePath?.let { path ->
                    if (currentlyPlaying == path) {
                        MediaController.stop()
                    } else {
                        MediaController.play(
                            context = context,
                            filePath = path,
                            userSettings = userSettings,
                            onRoutingApplied = homeViewModel::reportAudioRoutingMessage
                        )
                    }
                }
            },
            isTtsButtonEnabled = !isStarted && !isTtsSpeaking && uiState.textInput.isNotEmpty() && uiState.transcript != null,
            isPlaybackButtonEnabled = !isStarted && !isTtsSpeaking && uiState.transcript?.wavFilePath?.isNotEmpty() == true,
            isPlaying = currentlyPlaying == uiState.transcript?.wavFilePath,
            isPlaybackActive = currentlyPlaying != null || isTtsSpeaking,
            isAsrModelLoading = isAsrModelLoading
        )

        if (isStarted) {
            WaveformDisplay(
                samples = uiState.latestAudioSamples,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(vertical = 8.dp)
            )
        }

        LaunchedEffect(uiState.transcript?.wavFilePath) {
            val transcript = uiState.transcript ?: return@LaunchedEffect
            if (transcript.wavFilePath.isEmpty()) return@LaunchedEffect
            val syncedTranscript = syncTranscriptCandidatesFromJsonl(transcript)
            if (syncedTranscript != transcript) {
                uiState = uiState.copy(
                    transcript = syncedTranscript,
                    localCandidate = syncedTranscript.localCandidates.firstOrNull()
                        ?: uiState.localCandidate,
                    remoteCandidates = if (syncedTranscript.remoteCandidates.isNotEmpty()) {
                        syncedTranscript.remoteCandidates
                    } else {
                        uiState.remoteCandidates
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(uiState.candidates) { index, candidate ->
                Text(
                    text = "${index + 1}: $candidate",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isMutable) { uiState = uiState.copy(textInput = candidate) }
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp
                )
            }
        }

        if (uiState.isRecognizingSpeech || uiState.isFetchingCandidates) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
    }
}

private fun Transcript?.orEmptyLocalCandidates(): List<String> = this?.localCandidates ?: emptyList()

private fun stopAudio() {
    audioRecord?.stop()
    audioRecord?.release()
    audioRecord = null
    audioInputRoutingSession.release()
    audioInputRoutingSession = NoopAudioInputRoutingSession
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
                contentDescription = if (isPlaying) stringResource(id = R.string.stop) else stringResource(id = R.string.play),
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
                contentDescription = stringResource(id = R.string.speak_text),
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
