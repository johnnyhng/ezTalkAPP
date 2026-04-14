package tw.com.johnnyhng.eztalk.asr.speaker

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.com.johnnyhng.eztalk.asr.SimulateStreamingAsr
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.audio.AudioIOManager
import tw.com.johnnyhng.eztalk.asr.audio.AudioInputRoutingSession
import tw.com.johnnyhng.eztalk.asr.audio.NoopAudioInputRoutingSession
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import kotlin.math.max

internal data class SpeakerAsrState(
    val isRecording: Boolean = false,
    val isRecognizingSpeech: Boolean = false,
    val countdownProgress: Float = 0f,
    val partialText: String = "",
    val finalText: String = "",
    val finalTextVersion: Int = 0,
    val currentUtteranceVariants: List<String> = emptyList(),
    val finalUtteranceBundle: SpeakerAsrUtteranceBundle? = null
)

internal class SpeakerAsrController(
    private val context: Context,
    private val onStateChanged: (SpeakerAsrState) -> Unit,
    private val onAudioRoutingApplied: (String?, String?) -> Unit = { _, _ -> }
) {
    private val controllerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val audioIOManager = AudioIOManager(context)
    private val sampleRateInHz = 16000

    private var state = SpeakerAsrState()
    private var audioRecord: AudioRecord? = null
    private var audioInputRoutingSession: AudioInputRoutingSession = NoopAudioInputRoutingSession
    private var recordingJob: Job? = null
    private val flushChannel = Channel<Unit>(Channel.CONFLATED)
    private val utteranceBuffer = SpeakerAsrUtteranceBuffer()

    fun currentState(): SpeakerAsrState = state

    @SuppressLint("MissingPermission")
    fun start(userSettings: UserSettings) {
        if (state.isRecording || recordingJob?.isActive == true) return

        updateState {
            it.copy(
                isRecording = true,
                isRecognizingSpeech = false,
                countdownProgress = 0f,
                partialText = "",
                finalText = "",
                currentUtteranceVariants = emptyList(),
                finalUtteranceBundle = null
            )
        }

        recordingJob = controllerScope.launch {
            val samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
            val managedRecord = audioIOManager.createMicAudioRecord(
                sampleRateInHz = sampleRateInHz,
                channelConfig = AudioFormat.CHANNEL_IN_MONO,
                audioFormat = AudioFormat.ENCODING_PCM_16BIT,
                preferredInputDeviceId = userSettings.preferredAudioInputDeviceId
            )
            audioRecord = managedRecord.audioRecord
            audioInputRoutingSession = managedRecord.routingSession
            managedRecord.routingMessage?.let { Log.i(TAG, it) }
            audioIOManager.logMicRoutingPreparation(
                preferredInputDeviceId = userSettings.preferredAudioInputDeviceId,
                routingMessage = managedRecord.routingMessage
            )
            val readLogger = audioIOManager.createAudioInputReadLogger("SpeakerAsrController")
            if (audioRecord == null) {
                audioInputRoutingSession.release()
                audioInputRoutingSession = NoopAudioInputRoutingSession
                onAudioRoutingApplied(managedRecord.routingMessage, null)
                updateState { it.copy(isRecording = false) }
                recordingJob = null
                return@launch
            }

            SimulateStreamingAsr.resetVadSafely()

            launch(Dispatchers.IO) {
                val interval = 0.1
                val readBufferSize = (interval * sampleRateInHz).toInt()
                val buffer = ShortArray(readBufferSize)

                audioRecord?.startRecording()
                val activeInputLabel = audioIOManager.logMicRoutingActivation(
                    audioRecord = audioRecord,
                    preferredInputDeviceId = userSettings.preferredAudioInputDeviceId,
                    sampleRateInHz = sampleRateInHz,
                    bufferSize = readBufferSize,
                    audioSource = managedRecord.audioSource
                )
                onAudioRoutingApplied(
                    managedRecord.routingMessage,
                    activeInputLabel
                )
                while (state.isRecording) {
                    val ret = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    readLogger.onRead(ret, buffer, activeInputLabel)
                    if (ret > 0) {
                        val samples = FloatArray(ret) { i -> buffer[i] / 32768.0f }
                        samplesChannel.send(samples)
                    }
                }
                samplesChannel.close()
            }

            try {
                processAudio(samplesChannel, userSettings)
            } finally {
                updateState {
                    it.copy(
                        isRecording = false,
                        isRecognizingSpeech = false,
                        countdownProgress = 0f,
                        currentUtteranceVariants = emptyList()
                    )
                }
                recordingJob = null
                try {
                    audioRecord?.stop()
                } catch (_: IllegalStateException) {
                }
                audioRecord?.release()
                audioRecord = null
                audioInputRoutingSession.release()
                audioInputRoutingSession = NoopAudioInputRoutingSession
            }
        }
    }

    fun stop() {
        if (!state.isRecording) return
        updateState { it.copy(isRecording = false) }
        controllerScope.launch {
            flushChannel.send(Unit)
        }
    }

    fun dispose() {
        stop()
        controllerScope.cancel()
    }

    private suspend fun processAudio(
        samplesChannel: Channel<FloatArray>,
        userSettings: UserSettings
    ) {
        val lingerMs = userSettings.lingerMs.toLong()
        val partialIntervalMs = userSettings.partialIntervalMs.toLong()

        var buffer = arrayListOf<Float>()
        val keep = (sampleRateInHz / 1000) * 500
        var offset = 0
        val windowSize = 512
        var startOffset = 0
        var isSpeechStarted = false
        var startTime = System.currentTimeMillis()
        val utteranceSegments = mutableListOf<FloatArray>()
        var lastVadPacketAt = 0L
        var done = false

        while (!done) {
            val samples = samplesChannel.tryReceive().getOrNull()
            val flushRequest = flushChannel.tryReceive().getOrNull()

            if (samples == null) {
                if (flushRequest != null || samplesChannel.isClosedForReceive) {
                    done = true
                } else {
                    delay(10)
                }
            } else {
                buffer.addAll(samples.toList())
            }

            while (offset + windowSize < buffer.size) {
                val chunk = buffer.subList(offset, offset + windowSize).toFloatArray()
                SimulateStreamingAsr.acceptVadWaveformSafely(chunk)
                offset += windowSize
                if (!isSpeechStarted && SimulateStreamingAsr.isVadSpeechDetectedSafely()) {
                    isSpeechStarted = true
                    utteranceBuffer.reset()
                    updateState {
                        it.copy(
                            isRecognizingSpeech = true,
                            countdownProgress = 0f,
                            partialText = "",
                            finalText = "",
                            currentUtteranceVariants = emptyList(),
                            finalUtteranceBundle = null
                        )
                    }
                    startTime = System.currentTimeMillis()
                    startOffset = max(0, offset - windowSize - keep)
                }
            }

            if (isSpeechStarted) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > partialIntervalMs) {
                    val stream = SimulateStreamingAsr.recognizer.createStream()
                    try {
                        stream.acceptWaveform(buffer.subList(startOffset, offset).toFloatArray(), sampleRateInHz)
                        SimulateStreamingAsr.recognizer.decode(stream)
                        val result = SimulateStreamingAsr.recognizer.getResult(stream)
                        utteranceBuffer.add(result.text)
                        updateState {
                            it.copy(
                                partialText = result.text,
                                currentUtteranceVariants = utteranceBuffer.variants()
                            )
                        }
                    } finally {
                        stream.release()
                    }
                    startTime = System.currentTimeMillis()
                }
            }

            while (true) {
                val seg = SimulateStreamingAsr.popVadSegmentSafely() ?: break
                utteranceSegments.add(seg)
                lastVadPacketAt = System.currentTimeMillis()
            }

            if (utteranceSegments.isNotEmpty()) {
                val since = System.currentTimeMillis() - lastVadPacketAt
                updateState {
                    it.copy(countdownProgress = (since.toFloat() / lingerMs).coerceIn(0f, 1f))
                }

                if (since >= lingerMs || done || !state.isRecording) {
                    val concatenated = utteranceSegments.flatMap { it.toList() }.toFloatArray()
                    val stream = SimulateStreamingAsr.recognizer.createStream()
                    try {
                        stream.acceptWaveform(concatenated, sampleRateInHz)
                        SimulateStreamingAsr.recognizer.decode(stream)
                        val result = SimulateStreamingAsr.recognizer.getResult(stream)
                        utteranceBuffer.add(result.text)
                        val nextVersion = state.finalTextVersion + 1
                        val finalUtteranceBundle = utteranceBuffer.build(nextVersion)
                        Log.i(TAG, "Speaker ASR final result: ${result.text}")
                        updateState {
                            it.copy(
                                partialText = result.text,
                                finalText = result.text,
                                finalTextVersion = nextVersion,
                                currentUtteranceVariants = utteranceBuffer.variants(),
                                finalUtteranceBundle = finalUtteranceBundle
                            )
                        }
                    } catch (error: Exception) {
                        Log.e(TAG, "Speaker local ASR decode failed", error)
                    } finally {
                        stream.release()
                    }

                    utteranceSegments.clear()
                    utteranceBuffer.reset()
                    isSpeechStarted = false
                    buffer = arrayListOf()
                    offset = 0
                    startOffset = 0
                    updateState {
                        it.copy(
                            isRecognizingSpeech = false,
                            countdownProgress = 0f
                        )
                    }
                }
            }
        }
    }

    private fun updateState(transform: (SpeakerAsrState) -> SpeakerAsrState) {
        state = transform(state)
        onStateChanged(state)
    }
}

@Composable
internal fun rememberSpeakerAsrController(
    onAudioRoutingApplied: (String?, String?) -> Unit = { _, _ -> }
): Pair<SpeakerAsrController, SpeakerAsrState> {
    val context = androidx.compose.ui.platform.LocalContext.current
    var state by remember { mutableStateOf(SpeakerAsrState()) }
    val controller = remember {
        SpeakerAsrController(
            context = context,
            onStateChanged = { updatedState -> state = updatedState },
            onAudioRoutingApplied = onAudioRoutingApplied
        )
    }

    DisposableEffect(controller) {
        onDispose {
            controller.dispose()
        }
    }

    return controller to state
}
