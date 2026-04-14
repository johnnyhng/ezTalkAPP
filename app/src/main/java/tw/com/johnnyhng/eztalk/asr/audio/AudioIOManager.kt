package tw.com.johnnyhng.eztalk.asr.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.os.Build
import android.media.MediaRecorder
import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings
import java.util.Locale
import kotlin.math.abs

internal data class ManagedAudioRecord(
    val audioRecord: AudioRecord?,
    val bufferSize: Int,
    val audioSource: Int,
    val routingSession: AudioInputRoutingSession,
    val routingMessage: String? = null
)

internal data class ManagedMediaPlayer(
    val mediaPlayer: MediaPlayer?,
    val routingSession: AudioOutputRoutingSession,
    val routingMessage: String? = null
)

internal class AudioIOManager(
    private val context: Context,
    private val audioRoutingRepository: AudioRoutingRepository = AudioRoutingRepository(context)
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun createMicAudioRecord(
        sampleRateInHz: Int,
        channelConfig: Int,
        audioFormat: Int,
        preferredInputDeviceId: Int?,
        audioSource: Int = MediaRecorder.AudioSource.MIC
    ): ManagedAudioRecord {
        val selectedInputType = audioRoutingRepository.resolveSelectedInputType(preferredInputDeviceId)
        val routingSession = prepareAudioInputRoutingSession(selectedInputType)
        val resolvedAudioSource = resolvePreferredAudioSource(
            selectedInputType = selectedInputType,
            defaultAudioSource = audioSource
        )
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        if (minBufferSize <= 0) {
            val message = "AudioRecord min buffer unavailable: size=$minBufferSize"
            Log.e(TAG, message)
            return ManagedAudioRecord(
                audioRecord = null,
                bufferSize = minBufferSize,
                audioSource = resolvedAudioSource,
                routingSession = routingSession,
                routingMessage = message
            )
        }

        val audioRecord = AudioRecord(
            resolvedAudioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            minBufferSize * 2
        )

        // Log internal state before applying routing
        Log.d(TAG, "AudioRecord initialized: source=$resolvedAudioSource rate=$sampleRateInHz " +
            "state=${describeRecordState(audioRecord.state)} sessionId=${audioRecord.audioSessionId}")

        val routingMessage = audioRoutingRepository.applyPreferredInputDevice(
            audioRecord = audioRecord,
            selectedInputDeviceId = preferredInputDeviceId
        )

        return ManagedAudioRecord(
            audioRecord = audioRecord,
            bufferSize = minBufferSize,
            audioSource = resolvedAudioSource,
            routingSession = routingSession,
            routingMessage = routingMessage
        )
    }

    private fun describeRecordState(state: Int): String = when (state) {
        AudioRecord.STATE_INITIALIZED -> "INITIALIZED"
        AudioRecord.STATE_UNINITIALIZED -> "UNINITIALIZED"
        else -> "UNKNOWN($state)"
    }

    fun logMicRoutingPreparation(
        preferredInputDeviceId: Int?,
        routingMessage: String?,
        requestedAudioSource: Int = MediaRecorder.AudioSource.MIC
    ) {
        val selectedLabel = audioRoutingRepository.resolveSelectedInputLabel(preferredInputDeviceId)
            ?: "System default"
        val selectedInputType = audioRoutingRepository.resolveSelectedInputType(preferredInputDeviceId)
        val resolvedAudioSource = resolvePreferredAudioSource(
            selectedInputType = selectedInputType,
            defaultAudioSource = requestedAudioSource
        )
        val availableInputs = audioRoutingRepository.describeAvailableInputDevices()
        val audioManagerState = audioRoutingRepository.describeAudioManagerState()
        val scoAvailable = audioManager.isBluetoothScoAvailableOffCall
        Log.i(
            TAG,
            "Audio input prepare: selected=$selectedLabel selectedId=${preferredInputDeviceId ?: "default"} " +
                "requestedAudioSource=${describeAudioSource(requestedAudioSource)} " +
                "resolvedAudioSource=${describeAudioSource(resolvedAudioSource)} " +
                "scoAvailable=$scoAvailable available=[$availableInputs] " +
                "routingMessage=${routingMessage ?: "none"} " +
                "audioManagerState=[$audioManagerState]"
        )
    }

    fun logMicRoutingActivation(
        audioRecord: AudioRecord?,
        preferredInputDeviceId: Int?,
        sampleRateInHz: Int,
        bufferSize: Int,
        audioSource: Int = MediaRecorder.AudioSource.MIC
    ): String? {
        val activeInputLabel = resolveActiveInputLabel(audioRecord)
        val selectedLabel = audioRoutingRepository.resolveSelectedInputLabel(preferredInputDeviceId)
            ?: "System default"
        val recordingState = audioRecord?.recordingState
        val audioManagerState = audioRoutingRepository.describeAudioManagerState()
        
        Log.i(
            TAG,
            "Audio input active: selected=$selectedLabel selectedId=${preferredInputDeviceId ?: "default"} " +
                "active=${activeInputLabel ?: "unknown"} sampleRate=$sampleRateInHz bufferSize=$bufferSize " +
                "audioSource=${describeAudioSource(audioSource)} recordingState=${describeRecordingState(recordingState)} " +
                "audioManagerState=[$audioManagerState] actualScoOn=${audioManager.isBluetoothScoOn}"
        )
        return activeInputLabel
    }

    private fun describeRecordingState(state: Int?): String = when (state) {
        AudioRecord.RECORDSTATE_RECORDING -> "RECORDING"
        AudioRecord.RECORDSTATE_STOPPED -> "STOPPED"
        null -> "NULL"
        else -> "UNKNOWN($state)"
    }

    fun createAudioInputReadLogger(sessionName: String): AudioInputReadLogger {
        return AudioInputReadLogger(sessionName)
    }

    fun resolveActiveInputLabel(audioRecord: AudioRecord?): String? {
        if (audioRecord == null) return null
        return audioRoutingRepository.resolveActiveInputLabel(audioRecord)
    }

    fun createPlaybackMediaPlayer(
        filePath: String,
        userSettings: UserSettings
    ): ManagedMediaPlayer {
        val preferredOutputDeviceId = userSettings.preferredAudioOutputDeviceId
        val routingSession = prepareAudioOutputRoutingSession(preferredOutputDeviceId)

        return try {
            val player = MediaPlayer().apply {
                setAudioAttributes(buildPlaybackAttributes(userSettings.allowAppAudioCapture))
                setDataSource(filePath)
            }
            val routingMessage = audioRoutingRepository.applyPreferredOutputDevice(
                mediaPlayer = player,
                selectedOutputDeviceId = preferredOutputDeviceId
            )
            ManagedMediaPlayer(
                mediaPlayer = player,
                routingSession = routingSession,
                routingMessage = routingMessage
            )
        } catch (error: Exception) {
            Log.e(TAG, "Failed to create playback media player for $filePath", error)
            routingSession.release()
            ManagedMediaPlayer(
                mediaPlayer = null,
                routingSession = NoopAudioOutputRoutingSession,
                routingMessage = "Playback initialization failed"
            )
        }
    }

    fun logPlaybackRoutingPreparation(
        filePath: String,
        preferredOutputDeviceId: Int?,
        routingMessage: String?
    ) {
        val selectedLabel = audioRoutingRepository.resolveSelectedOutputLabel(preferredOutputDeviceId)
            ?: "System default"
        val availableOutputs = audioRoutingRepository.describeAvailableOutputDevices()
        val communicationOutput = audioRoutingRepository.resolveCommunicationOutputLabel() ?: "none"
        val audioManagerState = audioRoutingRepository.describeAudioManagerState()
        Log.i(
            TAG,
            "Audio output prepare: file=${filePath.substringAfterLast('/')} " +
                "selected=$selectedLabel selectedId=${preferredOutputDeviceId ?: "default"} " +
                "communication=$communicationOutput available=[$availableOutputs] " +
                "routingMessage=${routingMessage ?: "none"} audioManagerState=[$audioManagerState]"
        )
    }

    fun logPlaybackRoutingActivation(
        filePath: String,
        preferredOutputDeviceId: Int?
    ) {
        val selectedLabel = audioRoutingRepository.resolveSelectedOutputLabel(preferredOutputDeviceId)
            ?: "System default"
        val communicationOutput = audioRoutingRepository.resolveCommunicationOutputLabel() ?: "none"
        val audioManagerState = audioRoutingRepository.describeAudioManagerState()
        Log.i(
            TAG,
            "Audio output active: file=${filePath.substringAfterLast('/')} " +
                "selected=$selectedLabel selectedId=${preferredOutputDeviceId ?: "default"} " +
                "communication=$communicationOutput audioManagerState=[$audioManagerState]"
        )
    }

    fun createSpeechOutputDriver(
        preferredLocale: Locale? = null,
        preferredOutputDeviceId: Int? = null,
        onStateChanged: (SpeechOutputState) -> Unit
    ): SpeechOutputController {
        return SpeechOutputController(
            context = context,
            preferredLocale = preferredLocale,
            preferredOutputDeviceId = preferredOutputDeviceId,
            audioRoutingRepository = audioRoutingRepository,
            onStateChanged = onStateChanged
        )
    }

    private fun buildPlaybackAttributes(allowAppAudioCapture: Boolean): AudioAttributes {
        val builder = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)

        resolvePlaybackCapturePolicy(
            allowAppAudioCapture = allowAppAudioCapture,
            sdkInt = Build.VERSION.SDK_INT
        )?.let { capturePolicy ->
            builder.setAllowedCapturePolicy(capturePolicy)
        }

        return builder.build()
    }

    private fun prepareAudioOutputRoutingSession(preferredOutputDeviceId: Int?): AudioOutputRoutingSession {
        if (preferredOutputDeviceId == null) return NoopAudioOutputRoutingSession

        val device = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.id == preferredOutputDeviceId } ?: return NoopAudioOutputRoutingSession

        val previousMode = audioManager.mode
        Log.i(TAG, "Audio output routing session prepare: device=${device.productName} (type=${device.type}) previousMode=${describeAudioMode(previousMode)}")

        // Force communication mode for specific routing
        if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || 
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.setCommunicationDevice(device)
        } else {
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                audioManager.isSpeakerphoneOn = true
            }
        }

        return AudioOutputRoutingSessionImpl(
            audioManager = audioManager,
            previousMode = previousMode
        )
    }

    private fun prepareAudioInputRoutingSession(selectedInputType: Int?): AudioInputRoutingSession {
        if (selectedInputType != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return NoopAudioInputRoutingSession
        }

        val previousMode = audioManager.mode
        val previousScoState = audioManager.isBluetoothScoOn
        val scoAvailable = audioManager.isBluetoothScoAvailableOffCall
        Log.i(
            TAG,
            "Audio input bluetooth session prepare: previousMode=${describeAudioMode(previousMode)} " +
                "previousScoOn=$previousScoState scoAvailable=$scoAvailable"
        )
        
        // Pixel recommendation: set mode before starting SCO
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.startBluetoothSco()
        @Suppress("DEPRECATION")
        audioManager.isBluetoothScoOn = true
        
        Log.i(
            TAG,
            "Audio input bluetooth session active: requestedMode=MODE_IN_COMMUNICATION " +
                "actualMode=${describeAudioMode(audioManager.mode)} scoOn=${audioManager.isBluetoothScoOn}"
        )

        return BluetoothScoAudioInputRoutingSession(
            audioManager = audioManager,
            previousMode = previousMode,
            previousScoState = previousScoState
        )
    }
}

internal interface AudioInputRoutingSession {
    fun release()
}

internal object NoopAudioInputRoutingSession : AudioInputRoutingSession {
    override fun release() = Unit
}

internal interface AudioOutputRoutingSession {
    fun release()
}

internal object NoopAudioOutputRoutingSession : AudioOutputRoutingSession {
    override fun release() = Unit
}

internal class AudioOutputRoutingSessionImpl(
    private val audioManager: AudioManager,
    private val previousMode: Int
) : AudioOutputRoutingSession {
    override fun release() {
        Log.d(TAG, "Releasing audio output routing session")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = previousMode
    }
}

internal class BluetoothScoAudioInputRoutingSession(
    private val audioManager: AudioManager,
    private val previousMode: Int,
    private val previousScoState: Boolean
) : AudioInputRoutingSession {
    private var released = false

    override fun release() {
        if (released) return
        released = true
        @Suppress("DEPRECATION")
        audioManager.stopBluetoothSco()
        @Suppress("DEPRECATION")
        audioManager.isBluetoothScoOn = previousScoState
        audioManager.mode = previousMode
        Log.i(
            TAG,
            "Audio input bluetooth session released: restoredMode=${describeAudioMode(audioManager.mode)} " +
                "restoredScoOn=${audioManager.isBluetoothScoOn}"
        )
    }
}

internal class AudioInputReadLogger(
    private val sessionName: String
) {
    private var readCount = 0
    private var errorCount = 0
    private var zeroAmplitudeCount = 0
    private var loggedFirstRead = false
    private var loggedFirstSignal = false

    fun onRead(ret: Int, buffer: ShortArray, activeInputLabel: String?) {
        readCount += 1

        if (ret <= 0) {
            errorCount += 1
            if (errorCount <= 3 || errorCount % 20 == 0) {
                Log.w(
                    TAG,
                    "Audio input read issue: session=$sessionName active=${activeInputLabel ?: "unknown"} ret=$ret readCount=$readCount errorCount=$errorCount"
                )
            }
            return
        }

        var peakAbs = 0
        var sumAbs = 0L
        var isAllZeros = true
        for (index in 0 until ret) {
            val amplitude = abs(buffer[index].toInt())
            if (amplitude > 0) isAllZeros = false
            if (amplitude > peakAbs) peakAbs = amplitude
            sumAbs += amplitude
        }
        val avgAbs = if (ret > 0) sumAbs / ret else 0L

        if (isAllZeros) zeroAmplitudeCount++

        if (!loggedFirstRead) {
            loggedFirstRead = true
            Log.i(
                TAG,
                "Audio input first read: session=$sessionName active=${activeInputLabel ?: "unknown"} frames=$ret peakAbs=$peakAbs avgAbs=$avgAbs isAllZeros=$isAllZeros"
            )
        }

        if (!loggedFirstSignal && peakAbs >= 500) {
            loggedFirstSignal = true
            Log.i(
                TAG,
                "Audio input signal detected: session=$sessionName active=${activeInputLabel ?: "unknown"} readCount=$readCount peakAbs=$peakAbs avgAbs=$avgAbs"
            )
        }

        if (readCount % 50 == 0 && !loggedFirstSignal) {
            Log.w(
                TAG,
                "Audio input silent/weak: session=$sessionName active=${activeInputLabel ?: "unknown"} " +
                    "readCount=$readCount zerosCount=$zeroAmplitudeCount lastPeak=$peakAbs lastAvg=$avgAbs"
            )
        }
    }
}

internal fun resolvePlaybackCapturePolicy(
    allowAppAudioCapture: Boolean,
    sdkInt: Int
): Int? {
    if (sdkInt < Build.VERSION_CODES.Q) return null
    return if (allowAppAudioCapture) {
        AudioAttributes.ALLOW_CAPTURE_BY_ALL
    } else {
        AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM
    }
}

internal fun resolvePreferredAudioSource(
    selectedInputType: Int?,
    defaultAudioSource: Int
): Int {
    return when (selectedInputType) {
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        else -> defaultAudioSource
    }
}

internal fun describeAudioSource(audioSource: Int): String {
    return when (audioSource) {
        MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
        MediaRecorder.AudioSource.MIC -> "MIC"
        MediaRecorder.AudioSource.VOICE_UPLINK -> "VOICE_UPLINK"
        MediaRecorder.AudioSource.VOICE_DOWNLINK -> "VOICE_DOWNLINK"
        MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
        MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
        MediaRecorder.AudioSource.VOICE_PERFORMANCE -> "VOICE_PERFORMANCE"
        else -> "AudioSource($audioSource)"
    }
}
