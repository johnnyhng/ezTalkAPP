package tw.com.johnnyhng.eztalk.asr.screens

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import kotlin.math.max

internal data class SpeakerAsrState(
    val isRecording: Boolean = false,
    val isRecognizingSpeech: Boolean = false,
    val countdownProgress: Float = 0f,
    val partialText: String = "",
    val finalText: String = ""
)

internal class SpeakerAsrController(
    private val context: Context,
    private val onStateChanged: (SpeakerAsrState) -> Unit
) {
    private val controllerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val sampleRateInHz = 16000

    private var state = SpeakerAsrState()
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val flushChannel = Channel<Unit>(Channel.CONFLATED)

    fun currentState(): SpeakerAsrState = state

    @SuppressLint("MissingPermission")
    fun start(userSettings: UserSettings) {
        if (state.isRecording || recordingJob?.isActive == true) return

        updateState {
            SpeakerAsrState(
                isRecording = true
            )
        }

        recordingJob = controllerScope.launch {
            val samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRateInHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateInHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            SimulateStreamingAsr.resetVadSafely()

            launch(Dispatchers.IO) {
                val interval = 0.1
                val readBufferSize = (interval * sampleRateInHz).toInt()
                val buffer = ShortArray(readBufferSize)

                audioRecord?.startRecording()
                while (state.isRecording) {
                    val ret = audioRecord?.read(buffer, 0, buffer.size) ?: -1
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
                        countdownProgress = 0f
                    )
                }
                recordingJob = null
                try {
                    audioRecord?.stop()
                } catch (_: IllegalStateException) {
                }
                audioRecord?.release()
                audioRecord = null
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
        val lingerMs = userSettings.lingerMs
        val partialIntervalMs = userSettings.partialIntervalMs

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
                    updateState {
                        it.copy(
                            isRecognizingSpeech = true,
                            countdownProgress = 0f
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
                        if (result.text.isNotBlank()) {
                            updateState {
                                it.copy(partialText = result.text)
                            }
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
                        if (result.text.isNotBlank()) {
                            updateState {
                                it.copy(
                                    partialText = result.text,
                                    finalText = result.text
                                )
                            }
                        }
                    } catch (error: Exception) {
                        Log.e(TAG, "Speaker local ASR decode failed", error)
                    } finally {
                        stream.release()
                    }

                    utteranceSegments.clear()
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
internal fun rememberSpeakerAsrController(): Pair<SpeakerAsrController, SpeakerAsrState> {
    val context = androidx.compose.ui.platform.LocalContext.current
    var state by remember { mutableStateOf(SpeakerAsrState()) }
    val controller = remember {
        SpeakerAsrController(context) { updatedState ->
            state = updatedState
        }
    }

    DisposableEffect(controller) {
        onDispose {
            controller.dispose()
        }
    }

    return controller to state
}
