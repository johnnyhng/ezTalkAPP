package tw.com.johnnyhng.eztalk.asr.managers

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tw.com.johnnyhng.eztalk.asr.SimulateStreamingAsr
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.audio.AudioIOManager
import tw.com.johnnyhng.eztalk.asr.audio.AudioInputRoutingSession
import tw.com.johnnyhng.eztalk.asr.audio.NoopAudioInputRoutingSession
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import tw.com.johnnyhng.eztalk.asr.tse.TseAudioPreprocessor
import tw.com.johnnyhng.eztalk.asr.tse.TseChunkOutput
import tw.com.johnnyhng.eztalk.asr.tse.initializeNativeTseForUser
import tw.com.johnnyhng.eztalk.asr.utterance.AsrUtteranceVariantBuffer
import tw.com.johnnyhng.eztalk.asr.utils.buildTranscriptFileTargets
import tw.com.johnnyhng.eztalk.asr.utils.saveAsWav
import tw.com.johnnyhng.eztalk.asr.utils.saveJsonl
import tw.com.johnnyhng.eztalk.asr.utils.buildWavHeaderBytes
import tw.com.johnnyhng.eztalk.asr.utils.floatSamplesToPcm16
import java.io.File
import java.io.FileOutputStream
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
    private val audioIOManager = AudioIOManager(context)
    private var audioRecord: AudioRecord? = null
    private var audioInputRoutingSession: AudioInputRoutingSession = NoopAudioInputRoutingSession
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
    private val utteranceVariantBuffer = AsrUtteranceVariantBuffer()
    private var recordingSessionId = 0L

    // Callbacks for UI updates
    var onPartialResult: (String) -> Unit = {}
    var onFinalResult: (Transcript) -> Unit = {}
    var onError: (String) -> Unit = {}
    var onAudioRoutingApplied: (String?, String?) -> Unit = { _, _ -> }

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
        val activeJob = recognitionJob
        if (_isStarted.value) {
            Log.w(TAG, "RecognitionManager start ignored: recorder already started")
            return
        }
        if (activeJob?.isActive == true) {
            Log.w(TAG, "RecognitionManager start deferred: previous session still finishing")
            scope.launch {
                runCatching { activeJob.join() }
                start(userSettings, mode, dataCollectText)
            }
            return
        }
        _currentDataCollectText.value = dataCollectText
        _isStarted.value = true
        while (flushChannel.tryReceive().isSuccess) {
            // Drain any stale stop signal from the previous session before launching a new one.
        }
        val sessionId = ++recordingSessionId
        Log.i(TAG, "RecognitionManager start: sessionId=$sessionId, mode=$mode, useTseDetection=${userSettings.useTseDetection}")

        recognitionJob = scope.launch {
            val samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
            
            // Audio setup
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
            val readLogger = audioIOManager.createAudioInputReadLogger("RecognitionManager")
            if (audioRecord == null) {
                audioInputRoutingSession.release()
                audioInputRoutingSession = NoopAudioInputRoutingSession
                onError("Unable to initialize microphone recorder")
                _isStarted.value = false
                recognitionJob = null
                return@launch
            }
            
            SimulateStreamingAsr.resetVadSafely()

            // Recording loop
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
                while (_isStarted.value) {
                    // Check for external playback activity - we assume external means TTS/Media which we don't have direct ref here
                    // but we can at least ensure we don't read if we are stop requested
                    val ret = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    readLogger.onRead(ret, buffer, activeInputLabel)
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
                processAudio(samplesChannel, userSettings, mode, sessionId)
            } finally {
                _isStarted.value = false
                _isRecognizingSpeech.value = false
                _countdownProgress.value = 0f
                Log.i(TAG, "RecognitionManager session finished: sessionId=$sessionId")
                recognitionJob = null
            }
        }
    }

    fun stop() {
        val sessionId = recordingSessionId
        Log.i(TAG, "RecognitionManager stop requested: sessionId=$sessionId")
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
            audioInputRoutingSession.release()
            audioInputRoutingSession = NoopAudioInputRoutingSession
        }
    }

    private suspend fun processAudio(
        samplesChannel: Channel<FloatArray>,
        userSettings: UserSettings,
        mode: RecordingMode,
        sessionId: Long
    ) {
        val lingerMs = userSettings.lingerMs
        val partialIntervalMs = userSettings.partialIntervalMs
        val saveVadSegmentsOnly = userSettings.saveVadSegmentsOnly
        val userId = userSettings.userId
        val nativeTse = if (userSettings.useTseDetection) {
            initializeNativeTseForUser(context, userId)
        } else {
            null
        }
        val tsePreprocessor = nativeTse?.let(::TseAudioPreprocessor)
        Log.i(
            TAG,
            "RecognitionManager processAudio: sessionId=$sessionId, userId=$userId, useTseDetection=${userSettings.useTseDetection}, nativeTseReady=${nativeTse != null}, saveVadSegmentsOnly=$saveVadSegmentsOnly"
        )

        var buffer = arrayListOf<Float>()
        var rawAlignedBuffer = arrayListOf<Float>()
        val keep = (sampleRateInHz / 1000) * 500
        var offset = 0
        val windowSize = 512
        var startOffset = 0
        var lastSpeechDetectedOffset = 0
        var isSpeechStarted = false
        var startTime = System.currentTimeMillis()
        val utteranceSegments = mutableListOf<FloatArray>()
        val rawUtteranceSegments = mutableListOf<FloatArray>()
        var lastVadPacketAt = 0L
        var rawSegmentSearchCursor = 0

        var done = false
        try {
            while (!done) {
                val s = samplesChannel.tryReceive().getOrNull()
                val flushRequest = flushChannel.tryReceive().getOrNull()

                if (s == null) {
                    if (flushRequest != null || samplesChannel.isClosedForReceive) {
                        tsePreprocessor?.flush()?.let { output ->
                            buffer.addAll(output.processed.toList())
                            rawAlignedBuffer.addAll(output.rawAligned.toList())
                        }
                        Log.i(TAG, "RecognitionManager flush received: sessionId=$sessionId, closing processing loop")
                        done = true
                    } else {
                        delay(10)
                    }
                } else {
                    val emitted = tsePreprocessor?.processChunk(s)
                    if (emitted != null) {
                        buffer.addAll(emitted.processed.toList())
                        rawAlignedBuffer.addAll(emitted.rawAligned.toList())
                    } else {
                        buffer.addAll(s.toList())
                    }
                }

                // VAD Processing
                while (offset + windowSize < buffer.size) {
                    val chunk = buffer.subList(offset, offset + windowSize).toFloatArray()
                    SimulateStreamingAsr.acceptVadWaveformSafely(chunk)
                    offset += windowSize
                    if (!isSpeechStarted && SimulateStreamingAsr.isVadSpeechDetectedSafely()) {
                        isSpeechStarted = true
                        utteranceVariantBuffer.reset()
                        _isRecognizingSpeech.value = true
                        startTime = System.currentTimeMillis()
                        Log.i(
                            TAG,
                            "RecognitionManager speech start detected: offset=$offset, bufferSize=${buffer.size}, rawAlignedBufferSize=${rawAlignedBuffer.size}, tseEnabled=${tsePreprocessor != null}"
                        )
                        rawSegmentSearchCursor = if (saveVadSegmentsOnly) {
                            offset - windowSize
                        } else {
                            0
                        }
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
                            utteranceVariantBuffer.add(result.text)
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
                    if (!saveVadSegmentsOnly) {
                        lastSpeechDetectedOffset = offset
                    } else if (tsePreprocessor != null) {
                        val rawSegmentRange = findSegmentRange(
                            source = buffer,
                            target = seg,
                            startIndex = rawSegmentSearchCursor
                        )
                        val rawSegment = if (rawSegmentRange != null) {
                            rawSegmentSearchCursor = rawSegmentRange.last + 1
                            rawAlignedBuffer.subList(
                                rawSegmentRange.first,
                                rawSegmentRange.last + 1
                            ).toFloatArray()
                        } else {
                            Log.w(TAG, "Failed to align raw segment with TSE-processed segment; using processed segment length as fallback")
                            seg.copyOf()
                        }
                        rawUtteranceSegments.add(rawSegment)
                    }
                    utteranceSegments.add(seg)
                    lastVadPacketAt = System.currentTimeMillis()
                }

                // Final Utterance Processing
                if (utteranceSegments.isNotEmpty()) {
                    val since = System.currentTimeMillis() - lastVadPacketAt
                    _countdownProgress.value = (since.toFloat() / lingerMs).coerceIn(0f, 1f)

                    if (since >= lingerMs || done || !_isStarted.value) {
                        Log.i(
                            TAG,
                            "RecognitionManager final utterance trigger: since=${since}ms, done=$done, started=${_isStarted.value}, segmentCount=${utteranceSegments.size}, tseEnabled=${tsePreprocessor != null}"
                        )
                        val concatenated = utteranceSegments.flatMap { it.toList() }.toFloatArray()
                        val audioToSave = if (saveVadSegmentsOnly) {
                            concatenated
                        } else {
                            lastSpeechDetectedOffset = min(buffer.size - 1, lastSpeechDetectedOffset + keep)
                            buffer.subList(startOffset, lastSpeechDetectedOffset).toFloatArray()
                        }
                        val rawAudioToSave = when {
                            tsePreprocessor == null -> null
                            saveVadSegmentsOnly -> rawUtteranceSegments.flatMap { it.toList() }.toFloatArray()
                            else -> rawAlignedBuffer.subList(startOffset, lastSpeechDetectedOffset).toFloatArray()
                        }

                        val stream = SimulateStreamingAsr.recognizer.createStream()
                        try {
                            stream.acceptWaveform(concatenated, sampleRateInHz)
                            SimulateStreamingAsr.recognizer.decode(stream)
                            val result = SimulateStreamingAsr.recognizer.getResult(stream)
                            utteranceVariantBuffer.add(result.text)
                            val utteranceBundle = utteranceVariantBuffer.build(version = 0)

                            val originalText = result.text
                            val isDataCollectMode = mode == RecordingMode.DATA_COLLECT
                            val modifiedText = if (isDataCollectMode) _currentDataCollectText.value else originalText
                            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
                            val filename = "${timestamp}.app"

                            val wavPath = saveAsWav(context, audioToSave, sampleRateInHz, 1, userId, filename)
                            if (wavPath != null) {
                                Log.i(
                                    TAG,
                                    "RecognitionManager processed wav saved: path=$wavPath, samples=${audioToSave.size}, tseEnabled=${tsePreprocessor != null}"
                                )
                                if (rawAudioToSave != null) {
                                    val rawWavPath = saveSiblingRawWav(
                                        userId = userId,
                                        baseFilename = timestamp,
                                        samples = rawAudioToSave
                                    )
                                    Log.i(
                                        TAG,
                                        "RecognitionManager raw sibling wav save: path=${rawWavPath ?: "null"}, samples=${rawAudioToSave.size}"
                                    )
                                } else {
                                    Log.i(
                                        TAG,
                                        "RecognitionManager raw sibling wav skipped: tseEnabled=${tsePreprocessor != null}, rawAudioAvailable=false"
                                    )
                                }
                                Log.d(
                                    TAG,
                                    "DataCollect jsonl update: reason=final_utterance, file=${File(wavPath).name}, modified=$modifiedText, checked=$isDataCollectMode, mutable=${!isDataCollectMode}, utteranceVariants=${utteranceBundle?.variants.orEmpty().size}:${utteranceBundle?.variants.orEmpty()}"
                                )
                                saveJsonl(
                                    context = context,
                                    userId = userId,
                                    filename = filename,
                                    originalText = originalText,
                                    modifiedText = modifiedText,
                                    checked = isDataCollectMode,
                                    mutable = !isDataCollectMode,
                                    utteranceVariants = utteranceBundle?.variants.orEmpty()
                                )

                                onFinalResult(Transcript(
                                    recognizedText = originalText,
                                    wavFilePath = wavPath,
                                    modifiedText = modifiedText,
                                    checked = isDataCollectMode,
                                    mutable = !isDataCollectMode,
                                    utteranceVariants = utteranceBundle?.variants.orEmpty(),
                                    localCandidates = listOf(originalText)
                                ))
                            } else {
                                Log.e(
                                    TAG,
                                    "RecognitionManager failed to save processed wav: filename=$filename, samples=${audioToSave.size}, tseEnabled=${tsePreprocessor != null}"
                                )
                            }
                        } finally {
                            stream.release()
                        }

                        // Reset for next utterance
                        utteranceSegments.clear()
                        rawUtteranceSegments.clear()
                        isSpeechStarted = false
                        buffer = arrayListOf()
                        rawAlignedBuffer = arrayListOf()
                        offset = 0
                        startOffset = 0
                        lastSpeechDetectedOffset = 0
                        lastVadPacketAt = 0L
                        startTime = System.currentTimeMillis()
                        rawSegmentSearchCursor = 0
                        utteranceVariantBuffer.reset()
                        SimulateStreamingAsr.resetVadSafely()
                        Log.i(TAG, "RecognitionManager utterance reset complete: VAD state cleared for next utterance")
                        _isRecognizingSpeech.value = false
                        _countdownProgress.value = 0f
                    }
                }
            }
        } finally {
            nativeTse?.release()
        }
    }

    private fun saveSiblingRawWav(
        userId: String,
        baseFilename: String,
        samples: FloatArray
    ): String? {
        val rawFilename = "$baseFilename.raw.app"
        val file = buildTranscriptFileTargets(context.filesDir, userId, rawFilename).wavFile
        return try {
            FileOutputStream(file).use { out ->
                val pcmData = floatSamplesToPcm16(samples)
                out.write(buildWavHeaderBytes(pcmData.size, sampleRateInHz, 1), 0, 44)
                out.write(pcmData)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving raw sibling WAV file: ${file.absolutePath}", e)
            null
        }
    }

    private fun findSegmentRange(
        source: List<Float>,
        target: FloatArray,
        startIndex: Int
    ): IntRange? {
        if (target.isEmpty()) return IntRange.EMPTY
        if (source.size < target.size || startIndex >= source.size) return null
        val lastStart = source.size - target.size
        for (index in max(0, startIndex)..lastStart) {
            var matched = true
            for (offset in target.indices) {
                if (source[index + offset] != target[offset]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                return index until (index + target.size)
            }
        }
        return null
    }

    fun updateDataCollectText(text: String) {
        _currentDataCollectText.value = text
    }
}
