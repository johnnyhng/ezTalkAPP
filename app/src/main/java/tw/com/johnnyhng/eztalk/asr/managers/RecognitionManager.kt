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
import tw.com.johnnyhng.eztalk.asr.tse.NativeTseWaveformPipeline
import tw.com.johnnyhng.eztalk.asr.tse.NativeTSE
import tw.com.johnnyhng.eztalk.asr.tse.TseAudioPreprocessor
import tw.com.johnnyhng.eztalk.asr.utterance.AsrUtteranceVariantBuffer
import tw.com.johnnyhng.eztalk.asr.utils.buildTranscriptFileTargets
import tw.com.johnnyhng.eztalk.asr.utils.saveAsWav
import tw.com.johnnyhng.eztalk.asr.utils.saveJsonl
import tw.com.johnnyhng.eztalk.asr.utils.buildWavHeaderBytes
import tw.com.johnnyhng.eztalk.asr.utils.floatSamplesToPcm16
import tw.com.johnnyhng.eztalk.asr.utils.sha256
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

class RecognitionManager(private val context: Context) {
    private companion object {
        const val VAD_TAG = "ezTalk-VAD"
    }

    private enum class RecordingMode {
        TRANSLATE,
        DATA_COLLECT,
    }

    private enum class VadInputSource {
        TSE_PROCESSED,
        RAW_FALLBACK_TSE_DISABLED,
        RAW_FALLBACK_TSE_INIT_FAILED,
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
    var onRecordingSessionFinished: (Long) -> Unit = {}

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
            val recordingLoop = launch(Dispatchers.IO) {
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
                withContext(NonCancellable) {
                    stopAudioInput()
                    recordingLoop.join()
                    releaseAudioInputAfterStop()
                }
                Log.i(TAG, "RecognitionManager session finished: sessionId=$sessionId")
                recognitionJob = null
                onRecordingSessionFinished(sessionId)
            }
        }
    }

    fun stop() {
        val sessionId = recordingSessionId
        Log.i(TAG, "RecognitionManager stop requested: sessionId=$sessionId")
        _isStarted.value = false
        scope.launch {
            flushChannel.send(Unit)
        }
    }

    private fun releaseAudioInput() {
        stopAudioInput()
        releaseAudioInputAfterStop()
    }

    private fun stopAudioInput() {
        val recorder = audioRecord
        if (recorder != null) {
            try {
                recorder.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "AudioRecord.stop() called in invalid state", e)
            }
        }
    }

    private fun releaseAudioInputAfterStop() {
        audioRecord?.release()
        audioRecord = null
        audioInputRoutingSession.release()
        audioInputRoutingSession = NoopAudioInputRoutingSession
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
        val dummyTseRequested = userSettings.useTseDetection
        var tseRuntimeName = "raw_passthrough"
        var vadInputSource = VadInputSource.RAW_FALLBACK_TSE_DISABLED
        val tsePreprocessor = if (dummyTseRequested) {
            TseAudioPreprocessor().let { preprocessor ->
                if (preprocessor.initialize(context)) {
                    preprocessor.reset()
                    tseRuntimeName = preprocessor.runtimeName
                    vadInputSource = VadInputSource.TSE_PROCESSED
                    preprocessor
                } else {
                    tseRuntimeName = preprocessor.runtimeName
                    vadInputSource = VadInputSource.RAW_FALLBACK_TSE_INIT_FAILED
                    null
                }
            }
        } else {
            null
        }
        Log.i(
            TAG,
            "RecognitionManager processAudio: sessionId=$sessionId, userId=$userId, useTseDetection=$dummyTseRequested, liveVadRuntime=$tseRuntimeName, vadInputSource=$vadInputSource, saveVadSegmentsOnly=$saveVadSegmentsOnly"
        )

        val postFinalizationVadGuardMs = 1_000L
        var vadInputBuffer = arrayListOf<Float>()
        var rawAlignedBuffer = arrayListOf<Float>()
        val keep = (sampleRateInHz / 1000) * 500
        var offset = 0
        val windowSize = 512
        var startOffset = 0
        var lastSpeechDetectedOffset = 0
        var isSpeechStarted = false
        var startTime = System.currentTimeMillis()
        val utteranceSegments = mutableListOf<FloatArray>()
        var lastVadPacketAt = 0L
        var processedChunkCount = 0
        var utteranceIndex = 1
        var vadGuardUntilMs = 0L
        var guardedSamples = 0
        var speechDetectedOffset = -1
        var speechDetectedRawAlignedSize = 0
        var speechDetectedVadInputSize = 0
        var speechDetectedAtMs = 0L

        var done = false
        try {
            while (!done) {
                val s = samplesChannel.tryReceive().getOrNull()
                val flushRequest = flushChannel.tryReceive().getOrNull()

                if (s == null) {
                    if (flushRequest != null || samplesChannel.isClosedForReceive) {
                        tsePreprocessor?.flush()?.let { tail ->
                            if (tail.processed.isNotEmpty()) {
                                rawAlignedBuffer.addAll(tail.rawAligned.toList())
                                vadInputBuffer.addAll(tail.processed.toList())
                            }
                        }
                        Log.i(TAG, "RecognitionManager flush received: sessionId=$sessionId, closing processing loop")
                        done = true
                    } else {
                        delay(10)
                    }
                } else {
                    if (s.isEmpty()) continue
                    val chunkOutput = tsePreprocessor?.processChunk(s)
                        ?: tw.com.johnnyhng.eztalk.asr.tse.TseChunkOutput(s, s)
                    if (chunkOutput.processed.isEmpty() && chunkOutput.rawAligned.isEmpty()) {
                        continue
                    }
                    val nowMs = System.currentTimeMillis()
                    if (nowMs < vadGuardUntilMs) {
                        guardedSamples += chunkOutput.processed.size
                        processedChunkCount += 1
                        if (processedChunkCount <= 5 || processedChunkCount % 25 == 0) {
                            Log.i(
                                TAG,
                                "RecognitionManager VAD guard active: sessionId=$sessionId, utterance=$utteranceIndex, chunk=$processedChunkCount, remainingMs=${vadGuardUntilMs - nowMs}, guardedSamples=$guardedSamples, rawRms=${rms(chunkOutput.rawAligned).format3()}, vadRms=${rms(chunkOutput.processed).format3()}"
                            )
                        }
                        continue
                    }
                    if (guardedSamples > 0) {
                        Log.i(
                            TAG,
                            "RecognitionManager VAD guard complete: sessionId=$sessionId, utterance=$utteranceIndex, guardedSamples=$guardedSamples, vadInputSource=$vadInputSource, liveVadRuntime=$tseRuntimeName"
                        )
                        guardedSamples = 0
                    }
                    rawAlignedBuffer.addAll(chunkOutput.rawAligned.toList())
                    vadInputBuffer.addAll(chunkOutput.processed.toList())
                    processedChunkCount += 1
                    if (processedChunkCount <= 5 || processedChunkCount % 25 == 0) {
                        Log.i(
                            TAG,
                            "RecognitionManager VAD input stats: sessionId=$sessionId, utterance=$utteranceIndex, chunk=$processedChunkCount, vadInputSource=$vadInputSource, liveVadRuntime=$tseRuntimeName, rawRms=${rms(chunkOutput.rawAligned).format3()}, vadRms=${rms(chunkOutput.processed).format3()}, rawPeak=${peakAbs(chunkOutput.rawAligned).format3()}, vadPeak=${peakAbs(chunkOutput.processed).format3()}, rmsRatio=${ratio(rms(chunkOutput.processed), rms(chunkOutput.rawAligned)).format3()}"
                        )
                        Log.i(
                            VAD_TAG,
                            "timeline_probe sessionId=$sessionId utterance=$utteranceIndex chunk=$processedChunkCount rawAlignedSize=${rawAlignedBuffer.size} vadInputSize=${vadInputBuffer.size} delta=${rawAlignedBuffer.size - vadInputBuffer.size} rawChunk=${chunkOutput.rawAligned.size} processedChunk=${chunkOutput.processed.size} rawRms=${rms(chunkOutput.rawAligned).format3()} processedRms=${rms(chunkOutput.processed).format3()} rawPeak=${peakAbs(chunkOutput.rawAligned).format3()} processedPeak=${peakAbs(chunkOutput.processed).format3()} rmsRatio=${ratio(rms(chunkOutput.processed), rms(chunkOutput.rawAligned)).format3()}"
                        )
                    }
                }

                // VAD Processing
                while (offset + windowSize < vadInputBuffer.size) {
                    val chunk = vadInputBuffer.subList(offset, offset + windowSize).toFloatArray()
                    SimulateStreamingAsr.acceptVadWaveformSafely(chunk)
                    offset += windowSize
                    if (!isSpeechStarted && SimulateStreamingAsr.isVadSpeechDetectedSafely()) {
                        isSpeechStarted = true
                        utteranceVariantBuffer.reset()
                        _isRecognizingSpeech.value = true
                        startTime = System.currentTimeMillis()
                        if (!saveVadSegmentsOnly) startOffset = max(0, offset - windowSize - keep)
                        speechDetectedOffset = offset
                        speechDetectedRawAlignedSize = rawAlignedBuffer.size
                        speechDetectedVadInputSize = vadInputBuffer.size
                        speechDetectedAtMs = System.currentTimeMillis()
                        val beforeStart = max(0, offset - keep)
                        val afterEnd = min(vadInputBuffer.size, offset + keep)
                        val vadBefore = vadInputBuffer.subList(beforeStart, offset).toFloatArray()
                        val vadAfter = vadInputBuffer.subList(offset, afterEnd).toFloatArray()
                        val rawBefore = rawAlignedBuffer.subList(beforeStart.coerceAtMost(rawAlignedBuffer.size), offset.coerceAtMost(rawAlignedBuffer.size)).toFloatArray()
                        val rawAfter = rawAlignedBuffer.subList(offset.coerceAtMost(rawAlignedBuffer.size), afterEnd.coerceAtMost(rawAlignedBuffer.size)).toFloatArray()
                        Log.i(
                            TAG,
                            "RecognitionManager speech start detected: sessionId=$sessionId, utterance=$utteranceIndex, offset=$offset, vadInputBufferSize=${vadInputBuffer.size}, rawAlignedBufferSize=${rawAlignedBuffer.size}, vadInputSource=$vadInputSource, liveVadRuntime=$tseRuntimeName"
                        )
                        Log.i(
                            VAD_TAG,
                            "speech_detected sessionId=$sessionId utterance=$utteranceIndex vadOffset=$offset saveStart=$startOffset preRollSamples=$keep vadInputSize=${vadInputBuffer.size} rawAlignedSize=${rawAlignedBuffer.size} rawRms=${rms(rawAlignedBuffer.takeLast(windowSize).toFloatArray()).format3()} vadRms=${rms(chunk).format3()} source=$vadInputSource runtime=$tseRuntimeName"
                        )
                        Log.i(
                            VAD_TAG,
                            "speech_detect_probe sessionId=$sessionId utterance=$utteranceIndex vadOffset=$offset rawAlignedAtDetect=$speechDetectedRawAlignedSize vadInputAtDetect=$speechDetectedVadInputSize timelineDelta=${speechDetectedRawAlignedSize - speechDetectedVadInputSize} saveStart=$startOffset preRollSamples=$keep rawBeforeRms=${rms(rawBefore).format3()} rawAfterRms=${rms(rawAfter).format3()} vadBeforeRms=${rms(vadBefore).format3()} vadAfterRms=${rms(vadAfter).format3()} rawBeforePeak=${peakAbs(rawBefore).format3()} rawAfterPeak=${peakAbs(rawAfter).format3()} vadBeforePeak=${peakAbs(vadBefore).format3()} vadAfterPeak=${peakAbs(vadAfter).format3()} beforeRmsRatio=${ratio(rms(vadBefore), rms(rawBefore)).format3()} afterRmsRatio=${ratio(rms(vadAfter), rms(rawAfter)).format3()}"
                        )
                    }
                }

                // Partial Result Processing
                if (isSpeechStarted) {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed > partialIntervalMs) {
                        val partialStartOffset = if (saveVadSegmentsOnly) 0 else startOffset.coerceAtMost(offset)
                        val partialInput = vadInputBuffer.subList(partialStartOffset, offset).toFloatArray()
                        val stream = SimulateStreamingAsr.recognizer.createStream()
                        try {
                            stream.acceptWaveform(partialInput, sampleRateInHz)
                            SimulateStreamingAsr.recognizer.decode(stream)
                            val result = SimulateStreamingAsr.recognizer.getResult(stream)
                            utteranceVariantBuffer.add(result.text)
                            Log.i(
                                VAD_TAG,
                                "partial_asr_probe sessionId=$sessionId utterance=$utteranceIndex elapsedMs=$elapsed inputRange=[$partialStartOffset,$offset) inputSamples=${partialInput.size} detectOffset=$speechDetectedOffset startMinusDetect=${if (speechDetectedOffset >= 0) partialStartOffset - speechDetectedOffset else 0} samplesSinceDetect=${if (speechDetectedOffset >= 0) offset - speechDetectedOffset else -1} msSinceDetect=${if (speechDetectedAtMs > 0L) System.currentTimeMillis() - speechDetectedAtMs else -1} text=${result.text}"
                            )
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
                    }
                    utteranceSegments.add(seg)
                    lastVadPacketAt = System.currentTimeMillis()
                    Log.i(
                        VAD_TAG,
                        "vad_segment sessionId=$sessionId utterance=$utteranceIndex segmentCount=${utteranceSegments.size} segmentSamples=${seg.size} lastSpeechOffset=$lastSpeechDetectedOffset currentOffset=$offset saveStart=$startOffset vadInputSize=${vadInputBuffer.size}"
                    )
                }

                // Final Utterance Processing
                if (utteranceSegments.isNotEmpty()) {
                    val since = System.currentTimeMillis() - lastVadPacketAt
                    _countdownProgress.value = (since.toFloat() / lingerMs).coerceIn(0f, 1f)

                    if (since >= lingerMs || done || !_isStarted.value) {
                        Log.i(
                            TAG,
                            "RecognitionManager final utterance trigger: sessionId=$sessionId, utterance=$utteranceIndex, since=${since}ms, done=$done, started=${_isStarted.value}, segmentCount=${utteranceSegments.size}, vadInputSource=$vadInputSource, liveVadRuntime=$tseRuntimeName"
                        )
                        val saveStartOffset = if (saveVadSegmentsOnly) 0 else startOffset
                        val rawAudioToSave = if (saveVadSegmentsOnly) {
                            utteranceSegments.flatMap { it.toList() }.toFloatArray()
                        } else {
                            lastSpeechDetectedOffset = min(vadInputBuffer.size - 1, lastSpeechDetectedOffset + keep)
                            rawAlignedBuffer.subList(startOffset, lastSpeechDetectedOffset).toFloatArray()
                        }
                        val rawPassthroughAudio = if (saveVadSegmentsOnly) {
                            utteranceSegments.flatMap { it.toList() }.toFloatArray()
                        } else {
                            vadInputBuffer.subList(startOffset, lastSpeechDetectedOffset).toFloatArray()
                        }
                        val saveEndOffset = if (saveVadSegmentsOnly) rawAudioToSave.size else lastSpeechDetectedOffset
                        Log.i(
                            VAD_TAG,
                            "final_trigger sessionId=$sessionId utterance=$utteranceIndex sinceMs=$since segmentCount=${utteranceSegments.size} saveRange=[$saveStartOffset,$saveEndOffset) savedSamples=${rawAudioToSave.size} passthroughSamples=${rawPassthroughAudio.size} rawAlignedSize=${rawAlignedBuffer.size} vadInputSize=${vadInputBuffer.size} source=$vadInputSource runtime=$tseRuntimeName realtimeProcessedRms=${rms(rawPassthroughAudio).format3()} realtimeProcessedPeak=${peakAbs(rawPassthroughAudio).format3()}"
                        )
                        Log.i(
                            VAD_TAG,
                            "final_range_probe sessionId=$sessionId utterance=$utteranceIndex detectOffset=$speechDetectedOffset saveStart=$saveStartOffset saveEnd=$saveEndOffset startMinusDetect=${if (speechDetectedOffset >= 0) saveStartOffset - speechDetectedOffset else 0} endMinusDetect=${if (speechDetectedOffset >= 0) saveEndOffset - speechDetectedOffset else 0} detectRawSize=$speechDetectedRawAlignedSize detectVadSize=$speechDetectedVadInputSize finalRawSize=${rawAlignedBuffer.size} finalVadSize=${vadInputBuffer.size}"
                        )
                        val isDataCollectMode = mode == RecordingMode.DATA_COLLECT
                        val shouldEndSessionAfterFinalUtterance =
                            mode == RecordingMode.DATA_COLLECT || mode == RecordingMode.TRANSLATE
                        val shouldPostProcessNativeTse = dummyTseRequested
                        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
                        val filename = "${timestamp}.app"
                        val rawWavPath = if (shouldPostProcessNativeTse) {
                            saveSiblingRawWav(
                                userId = userId,
                                baseFilename = timestamp,
                                samples = rawAudioToSave
                            )
                        } else {
                            null
                        }
                        if (shouldPostProcessNativeTse) {
                            Log.i(
                                TAG,
                                "RecognitionManager raw sibling wav completed before native TSE: path=${rawWavPath.orEmpty().ifBlank { "n/a" }}, samples=${rawAudioToSave.size}, isDataCollectMode=$isDataCollectMode"
                            )
                            Log.i(
                                VAD_TAG,
                                "raw_wav_saved sessionId=$sessionId utterance=$utteranceIndex path=${rawWavPath.orEmpty().ifBlank { "n/a" }} saveRange=[$saveStartOffset,$saveEndOffset) samples=${rawAudioToSave.size} rms=${rms(rawAudioToSave).format3()}"
                            )
                        }
                        val nativeTseResult = if (shouldPostProcessNativeTse) {
                            runNativeTseOffline(rawAudioToSave, sessionId)
                        } else {
                            null
                        }
                        val processedAudioToSave = if (shouldPostProcessNativeTse) {
                            nativeTseResult?.samples ?: rawPassthroughAudio
                        } else {
                            rawPassthroughAudio
                        }
                        
                        val rawPcm = floatSamplesToPcm16(rawAudioToSave)
                        val processedPcm = floatSamplesToPcm16(processedAudioToSave)
                        val rawHash = sha256(rawPcm)
                        val processedHash = sha256(processedPcm)
                        val rawRms = rms(rawAudioToSave)
                        val processedRms = rms(processedAudioToSave)
                        
                        val tseRuntime = when {
                            !shouldPostProcessNativeTse -> "realtime_only_${if (dummyTseRequested) "native_onnx" else "passthrough"}"
                            nativeTseResult != null -> nativeTseResult.runtime
                            else -> "offline_failed_fallback_realtime"
                        }
                        Log.i(
                            TAG,
                            "RecognitionManager TSE decision: sessionId=$sessionId, utterance=$utteranceIndex, shouldPostProcess=$shouldPostProcessNativeTse, nativeTseResult=${nativeTseResult != null}, liveVadRuntime=$tseRuntimeName, savedWavRuntime=$tseRuntime, vadInputSource=$vadInputSource, rawRms=${rawRms.format3()}, processedRms=${processedRms.format3()}"
                        )
                        Log.i(
                            TAG,
                            "RecognitionManager TSE integrity: sessionId=$sessionId, hashesMatch=${rawHash == processedHash}, rawHash=${rawHash.take(8)}, processedHash=${processedHash.take(8)}"
                        )
                        val rawAsrText = recognizeSamplesForLog(rawAudioToSave)
                        val realtimeProcessedAsrText = recognizeSamplesForLog(rawPassthroughAudio)
                        val processedAsrText = recognizeSamplesForLog(processedAudioToSave)
                        Log.i(
                            VAD_TAG,
                            "asr_compare sessionId=$sessionId utterance=$utteranceIndex saveRange=[$saveStartOffset,$saveEndOffset) rawText=$rawAsrText realtimeProcessedText=$realtimeProcessedAsrText offlineProcessedText=$processedAsrText rawSamples=${rawAudioToSave.size} realtimeProcessedSamples=${rawPassthroughAudio.size} offlineProcessedSamples=${processedAudioToSave.size} rawRms=${rawRms.format3()} realtimeProcessedRms=${rms(rawPassthroughAudio).format3()} offlineProcessedRms=${processedRms.format3()} rawPeak=${peakAbs(rawAudioToSave).format3()} realtimeProcessedPeak=${peakAbs(rawPassthroughAudio).format3()} offlineProcessedPeak=${peakAbs(processedAudioToSave).format3()} runtime=$tseRuntime"
                        )
                        Log.i(
                            VAD_TAG,
                            "tse_realtime_offline_compare sessionId=$sessionId utterance=$utteranceIndex saveRange=[$saveStartOffset,$saveEndOffset) realtimeHash=${sha256(floatSamplesToPcm16(rawPassthroughAudio)).take(8)} offlineHash=${processedHash.take(8)} hashesMatch=${floatSamplesToPcm16(rawPassthroughAudio).contentEquals(processedPcm)} rmsDelta=${(rms(rawPassthroughAudio) - processedRms).format3()} peakDelta=${(peakAbs(rawPassthroughAudio) - peakAbs(processedAudioToSave)).format3()} realtimeRuntime=$tseRuntimeName offlineRuntime=$tseRuntime"
                        )
                        val finalAsrInput = processedAudioToSave

                        val stream = SimulateStreamingAsr.recognizer.createStream()
                        try {
                            stream.acceptWaveform(finalAsrInput, sampleRateInHz)
                            SimulateStreamingAsr.recognizer.decode(stream)
                            val result = SimulateStreamingAsr.recognizer.getResult(stream)
                            utteranceVariantBuffer.add(result.text)
                            val utteranceBundle = utteranceVariantBuffer.build(version = 0)

                            val originalText = result.text
                            val modifiedText = if (isDataCollectMode) _currentDataCollectText.value else originalText

                            val wavPath = saveAsWav(context, processedAudioToSave, sampleRateInHz, 1, userId, filename)
                            if (wavPath != null) {
                                Log.i(
                                    TAG,
                                    "RecognitionManager processed wav saved: path=$wavPath, samples=${processedAudioToSave.size}, tseRequested=$dummyTseRequested, liveVadRuntime=$tseRuntimeName, savedWavRuntime=$tseRuntime, vadInputSource=$vadInputSource"
                                )
                                Log.i(
                                    VAD_TAG,
                                    "processed_wav_saved sessionId=$sessionId utterance=$utteranceIndex path=$wavPath saveRange=[$saveStartOffset,$saveEndOffset) rawSamples=${rawAudioToSave.size} processedSamples=${processedAudioToSave.size} rawRms=${rawRms.format3()} processedRms=${processedRms.format3()} runtime=$tseRuntime"
                                )
                                if (rawWavPath != null) {
                                    Log.i(
                                        TAG,
                                        "RecognitionManager raw sibling wav save: path=$rawWavPath, samples=${rawAudioToSave.size}"
                                    )
                                } else {
                                    Log.i(
                                        TAG,
                                        "RecognitionManager raw sibling wav skipped: tseRequested=$dummyTseRequested"
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
                                Log.i(
                                    VAD_TAG,
                                    "jsonl_saved sessionId=$sessionId utterance=$utteranceIndex filename=$filename saveRange=[$saveStartOffset,$saveEndOffset) checked=$isDataCollectMode variants=${utteranceBundle?.variants.orEmpty().size}"
                                )

                                onFinalResult(Transcript(
                                    recognizedText = originalText,
                                    wavFilePath = wavPath,
                                    rawWavFilePath = rawWavPath.orEmpty(),
                                    modifiedText = modifiedText,
                                    checked = isDataCollectMode,
                                    mutable = !isDataCollectMode,
                                    utteranceVariants = utteranceBundle?.variants.orEmpty(),
                                    localCandidates = listOf(originalText)
                                ))
                                Log.i(
                                    VAD_TAG,
                                    "processing_done sessionId=$sessionId utterance=$utteranceIndex filename=$filename saveRange=[$saveStartOffset,$saveEndOffset) wavPath=$wavPath rawPath=${rawWavPath.orEmpty()} restartMode=${if (shouldEndSessionAfterFinalUtterance) "${mode.name.lowercase(Locale.US)}_session_restart" else "continuous"}"
                                )
                            } else {
                                Log.e(
                                    TAG,
                                    "RecognitionManager failed to save processed wav: filename=$filename, samples=${processedAudioToSave.size}, tseRequested=$dummyTseRequested"
                                )
                            }
                        } finally {
                            stream.release()
                        }

                        // Reset for next utterance
                        val completedUtterance = utteranceIndex
                        val droppedQueuedSamples = drainQueuedSamples(samplesChannel)
                        if (droppedQueuedSamples > 0) {
                            Log.i(
                                TAG,
                                "RecognitionManager dropped queued audio after finalization: sessionId=$sessionId, completedUtterance=$completedUtterance, samples=$droppedQueuedSamples, reason=avoid_backlog_as_next_utterance"
                            )
                        }
                        _isRecognizingSpeech.value = false
                        _countdownProgress.value = 0f
                        if (shouldEndSessionAfterFinalUtterance) {
                            _isStarted.value = false
                            done = true
                            Log.i(TAG, "RecognitionManager utterance session complete: sessionId=$sessionId, mode=$mode, completedUtterance=$completedUtterance, reason=restart_after_session_finished")
                        } else {
                            utteranceSegments.clear()
                            isSpeechStarted = false
                            vadInputBuffer = arrayListOf()
                            rawAlignedBuffer = arrayListOf()
                            offset = 0
                            startOffset = 0
                            lastSpeechDetectedOffset = 0
                            lastVadPacketAt = 0L
                            startTime = System.currentTimeMillis()
                            utteranceIndex += 1
                            vadGuardUntilMs = System.currentTimeMillis() + postFinalizationVadGuardMs
                            guardedSamples = 0
                            speechDetectedOffset = -1
                            speechDetectedRawAlignedSize = 0
                            speechDetectedVadInputSize = 0
                            speechDetectedAtMs = 0L
                            utteranceVariantBuffer.reset()
                            tsePreprocessor?.reset()
                            SimulateStreamingAsr.resetVadSafely()
                            Log.i(TAG, "RecognitionManager utterance reset complete: sessionId=$sessionId, completedUtterance=$completedUtterance, nextUtterance=$utteranceIndex, vadInputSource=$vadInputSource, liveVadRuntime=$tseRuntimeName, vadGuardMs=$postFinalizationVadGuardMs")
                        }
                    }
                }
            }
        } finally {
            tsePreprocessor?.release()
        }
    }

    private fun recognizeSamplesForLog(samples: FloatArray): String {
        if (samples.isEmpty()) return ""
        val stream = SimulateStreamingAsr.recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRateInHz)
            SimulateStreamingAsr.recognizer.decode(stream)
            SimulateStreamingAsr.recognizer.getResult(stream).text
        } catch (e: Exception) {
            Log.w(VAD_TAG, "asr_compare_failed samples=${samples.size}", e)
            ""
        } finally {
            stream.release()
        }
    }

    private fun drainQueuedSamples(samplesChannel: Channel<FloatArray>): Int {
        var droppedSamples = 0
        while (true) {
            val queued = samplesChannel.tryReceive().getOrNull() ?: break
            droppedSamples += queued.size
        }
        return droppedSamples
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

    private data class NativeTseOfflineResult(
        val samples: FloatArray,
        val runtime: String
    )

    private fun runNativeTseOffline(samples: FloatArray, sessionId: Long): NativeTseOfflineResult? {
        if (samples.isEmpty()) return NativeTseOfflineResult(FloatArray(0), "native_onnx_lite_empty")

        val mode = NativeTSE.ACCELERATION_CPU
        return runNativeTseOfflineWithAcceleration(
            samples = samples,
            sessionId = sessionId,
            runtime = "native_onnx_lite_${NativeTSE.accelerationModeName(mode)}_offline",
            accelerationMode = mode
        )
    }

    private fun runNativeTseOfflineWithAcceleration(
        samples: FloatArray,
        sessionId: Long,
        runtime: String,
        accelerationMode: Int
    ): NativeTseOfflineResult? {
        val pipeline = NativeTseWaveformPipeline(context)
        return try {
            val initialized = pipeline.initialize(accelerationMode = accelerationMode)
            if (!initialized) {
                Log.w(TAG, "RecognitionManager native TSE offline skipped: sessionId=$sessionId, runtime=$runtime, reason=init_failed")
                return null
            }

            val processed = pipeline.process(samples)
            if (processed == null) {
                Log.w(TAG, "RecognitionManager native TSE offline failed: sessionId=$sessionId, runtime=$runtime, reason=process_failed")
                return null
            }
            Log.i(
                TAG,
                "RecognitionManager native TSE offline complete: sessionId=$sessionId, runtime=$runtime, inputSamples=${samples.size}, outputSamples=${processed.size}"
            )
            NativeTseOfflineResult(processed, runtime)
        } catch (t: Throwable) {
            Log.e(TAG, "RecognitionManager native TSE offline failed: sessionId=$sessionId, runtime=$runtime", t)
            null
        } finally {
            pipeline.close()
        }
    }

    private fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sumSquares = 0.0
        for (sample in samples) {
            sumSquares += sample * sample
        }
        return kotlin.math.sqrt(sumSquares / samples.size).toFloat()
    }

    private fun peakAbs(samples: FloatArray): Float {
        var peak = 0f
        for (sample in samples) {
            val abs = kotlin.math.abs(sample)
            if (abs > peak) peak = abs
        }
        return peak
    }

    private fun ratio(numerator: Float, denominator: Float): Float {
        if (denominator <= 1e-8f) return 0f
        return numerator / denominator
    }

    private fun Float.format3(): String = String.format(Locale.US, "%.3f", this)

    fun updateDataCollectText(text: String) {
        _currentDataCollectText.value = text
    }
}
