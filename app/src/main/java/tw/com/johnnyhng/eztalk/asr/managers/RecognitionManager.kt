package tw.com.johnnyhng.eztalk.asr.managers

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tw.com.johnnyhng.eztalk.asr.SimulateStreamingAsr
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import tw.com.johnnyhng.eztalk.asr.utils.saveAsWav
import tw.com.johnnyhng.eztalk.asr.utils.saveJsonl
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

class RecognitionManager(private val context: Context) {
    private enum class RecordingMode {
        TRANSLATE,
        DATA_COLLECT,
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var audioRecord: AudioRecord? = null
    private val sampleRateInHz = 16000

    private val _isStarted = MutableStateFlow(false)
    val isStarted = _isStarted.asStateFlow()

    private val _latestSamples = MutableStateFlow(FloatArray(0))
    val latestSamples = _latestSamples.asStateFlow()

    private val _isRecognizingSpeech = MutableStateFlow(false)
    val isRecognizingSpeech = _isRecognizingSpeech.asStateFlow()

    private val _countdownProgress = MutableStateFlow(0f)
    val countdownProgress = _countdownProgress.asStateFlow()
    private val _currentDataCollectText = MutableStateFlow("")

    // Internal channels
    private val flushChannel = Channel<Unit>(Channel.CONFLATED)
    private var recognitionJob: Job? = null

    // Callbacks for UI updates
    var onPartialResult: (String) -> Unit = {}
    var onFinalResult: (Transcript) -> Unit = {}
    var onError: (String) -> Unit = {}

    @SuppressLint("MissingPermission")
    fun startTranslate(userSettings: UserSettings) {
        start(userSettings, RecordingMode.TRANSLATE, "")
    }

    @SuppressLint("MissingPermission")
    fun startDataCollect(userSettings: UserSettings, dataCollectText: String) {
        start(userSettings, RecordingMode.DATA_COLLECT, dataCollectText)
    }

    @SuppressLint("MissingPermission")
    private fun start(userSettings: UserSettings, mode: RecordingMode, dataCollectText: String) {
        if (_isStarted.value || recognitionJob?.isActive == true) return
        _currentDataCollectText.value = dataCollectText
        _isStarted.value = true

        recognitionJob = scope.launch {
            val samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
            
            // Audio setup
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

            // Recording loop
            launch(Dispatchers.IO) {
                val interval = 0.1
                val readBufferSize = (interval * sampleRateInHz).toInt()
                val buffer = ShortArray(readBufferSize)

                audioRecord?.startRecording()
                while (_isStarted.value) {
                    val ret = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (ret > 0) {
                        val samples = FloatArray(ret) { i -> buffer[i] / 32768.0f }
                        samplesChannel.send(samples)
                        _latestSamples.value = samples
                    }
                }
                samplesChannel.close()
            }

            // Processing loop
            try {
                processAudio(samplesChannel, userSettings, mode)
            } finally {
                _isStarted.value = false
                _isRecognizingSpeech.value = false
                _countdownProgress.value = 0f
                recognitionJob = null
            }
        }
    }

    fun stop() {
        _isStarted.value = false
        scope.launch {
            flushChannel.send(Unit)
            try {
                audioRecord?.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "AudioRecord.stop() called in invalid state", e)
            }
            audioRecord?.release()
            audioRecord = null
        }
    }

    private suspend fun processAudio(
        samplesChannel: Channel<FloatArray>,
        userSettings: UserSettings,
        mode: RecordingMode
    ) {
        val lingerMs = userSettings.lingerMs
        val partialIntervalMs = userSettings.partialIntervalMs
        val saveVadSegmentsOnly = userSettings.saveVadSegmentsOnly
        val userId = userSettings.userId

        var buffer = arrayListOf<Float>()
        val keep = (sampleRateInHz / 1000) * 500
        var offset = 0
        val windowSize = 512
        var startOffset = 0
        var lastSpeechDetectedOffset = 0
        var isSpeechStarted = false
        var startTime = System.currentTimeMillis()
        val utteranceSegments = mutableListOf<FloatArray>()
        var lastVadPacketAt = 0L

        var done = false
        while (!done) {
            val s = samplesChannel.tryReceive().getOrNull()
            val flushRequest = flushChannel.tryReceive().getOrNull()

            if (s == null) {
                if (flushRequest != null || samplesChannel.isClosedForReceive) {
                    done = true
                } else {
                    delay(10)
                }
            } else {
                buffer.addAll(s.toList())
            }

            // VAD Processing
            while (offset + windowSize < buffer.size) {
                val chunk = buffer.subList(offset, offset + windowSize).toFloatArray()
                SimulateStreamingAsr.acceptVadWaveformSafely(chunk)
                offset += windowSize
                if (!isSpeechStarted && SimulateStreamingAsr.isVadSpeechDetectedSafely()) {
                    isSpeechStarted = true
                    _isRecognizingSpeech.value = true
                    startTime = System.currentTimeMillis()
                    if (!saveVadSegmentsOnly) startOffset = max(0, offset - windowSize - keep)
                }
            }

            // Partial Result Processing
            if (isSpeechStarted) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > partialIntervalMs) {
                    val stream = SimulateStreamingAsr.recognizer.createStream()
                    try {
                        stream.acceptWaveform(buffer.subList(0, offset).toFloatArray(), sampleRateInHz)
                        SimulateStreamingAsr.recognizer.decode(stream)
                        val result = SimulateStreamingAsr.recognizer.getResult(stream)
                        if (result.text.isNotBlank()) {
                            onPartialResult(result.text)
                        }
                    } finally {
                        stream.release()
                    }
                    startTime = System.currentTimeMillis()
                }
            }

            while (true) {
                val seg = SimulateStreamingAsr.popVadSegmentSafely() ?: break
                if (!saveVadSegmentsOnly) lastSpeechDetectedOffset = offset
                utteranceSegments.add(seg)
                lastVadPacketAt = System.currentTimeMillis()
            }

            // Final Utterance Processing
            if (utteranceSegments.isNotEmpty()) {
                val since = System.currentTimeMillis() - lastVadPacketAt
                _countdownProgress.value = (since.toFloat() / lingerMs).coerceIn(0f, 1f)

                if (since >= lingerMs || done || !_isStarted.value) {
                    val concatenated = utteranceSegments.flatMap { it.toList() }.toFloatArray()
                    val audioToSave = if (saveVadSegmentsOnly) {
                        concatenated
                    } else {
                        lastSpeechDetectedOffset = min(buffer.size - 1, lastSpeechDetectedOffset + keep)
                        buffer.subList(startOffset, lastSpeechDetectedOffset).toFloatArray()
                    }

                    val stream = SimulateStreamingAsr.recognizer.createStream()
                    try {
                        stream.acceptWaveform(concatenated, sampleRateInHz)
                        SimulateStreamingAsr.recognizer.decode(stream)
                        val result = SimulateStreamingAsr.recognizer.getResult(stream)
                        
                        val originalText = result.text
                        val isDataCollectMode = mode == RecordingMode.DATA_COLLECT
                        val modifiedText = if (isDataCollectMode) _currentDataCollectText.value else originalText
                        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
                        val filename = "${timestamp}.app"

                        val wavPath = saveAsWav(context, audioToSave, sampleRateInHz, 1, userId, filename)
                        if (wavPath != null) {
                            saveJsonl(context, userId, filename, originalText, modifiedText, isDataCollectMode, !isDataCollectMode)
                            
                            onFinalResult(Transcript(
                                recognizedText = originalText,
                                wavFilePath = wavPath,
                                modifiedText = modifiedText,
                                checked = isDataCollectMode,
                                mutable = !isDataCollectMode
                            ))
                        }
                    } finally {
                        stream.release()
                    }

                    // Reset for next utterance
                    utteranceSegments.clear()
                    isSpeechStarted = false
                    buffer = arrayListOf()
                    offset = 0
                    startOffset = 0
                    lastSpeechDetectedOffset = 0
                    _isRecognizingSpeech.value = false
                    _countdownProgress.value = 0f
                }
            }
        }
    }

    fun updateDataCollectText(text: String) {
        _currentDataCollectText.value = text
    }
}
