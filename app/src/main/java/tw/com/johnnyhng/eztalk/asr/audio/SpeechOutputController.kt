package tw.com.johnnyhng.eztalk.asr.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import tw.com.johnnyhng.eztalk.asr.TAG
import java.util.Locale
import java.util.UUID

internal data class SpeechOutputState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false
)

internal interface SpeechOutputDriver {
    fun initialize()
    fun speak(
        text: String,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: (() -> Unit)? = null
    ): Boolean
    fun stop()
    fun dispose()
}

internal class SpeechOutputController(
    private val context: Context,
    private val preferredLocale: Locale?,
    private val preferredOutputDeviceId: Int?,
    private val audioRoutingRepository: AudioRoutingRepository,
    private val onStateChanged: (SpeechOutputState) -> Unit
) : SpeechOutputDriver {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var state = SpeechOutputState()
    private var pendingOnStart: (() -> Unit)? = null
    private var pendingOnDone: (() -> Unit)? = null
    private var pendingOnError: (() -> Unit)? = null

    override fun initialize() {
        if (tts != null) return
        logSpeechOutputEnvironment("prepare")
        tts = TextToSpeech(context) { status ->
            val isReady = if (status == TextToSpeech.SUCCESS) {
                tts?.language = resolveLocale(tts)
                
                // Align TTS audio attributes with communication routing
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(attributes)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        updateState { it.copy(isSpeaking = true) }
                        logSpeechOutputEnvironment("start")
                        pendingOnStart?.invoke()
                    }

                    override fun onDone(utteranceId: String?) {
                        // Small delay to let hardware buffer finish
                        Thread.sleep(150)
                        updateState { it.copy(isSpeaking = false) }
                        releaseActiveRouting()
                        pendingOnDone?.also { callback ->
                            pendingOnDone = null
                            callback()
                        }
                        pendingOnStart = null
                        pendingOnError = null
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        updateState { it.copy(isSpeaking = false) }
                        releaseActiveRouting()
                        pendingOnError?.invoke()
                        pendingOnStart = null
                        pendingOnDone = null
                        pendingOnError = null
                    }
                })
                true
            } else {
                Log.e(TAG, "TTS initialization failed")
                false
            }
            updateState { it.copy(isReady = isReady, isSpeaking = false) }
        }
    }

    override fun speak(
        text: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: (() -> Unit)?
    ): Boolean {
        val engine = tts ?: return false
        logSpeechOutputEnvironment("speak")
        applyActiveRouting()
        pendingOnStart = onStart
        pendingOnDone = onDone
        pendingOnError = onError
        val utteranceId = UUID.randomUUID().toString()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            releaseActiveRouting()
            pendingOnStart = null
            pendingOnDone = null
            pendingOnError = null
            return false
        }
        return true
    }

    override fun stop() {
        pendingOnStart = null
        pendingOnDone = null
        pendingOnError = null
        tts?.stop()
        releaseActiveRouting()
        updateState { it.copy(isSpeaking = false) }
    }

    override fun dispose() {
        pendingOnStart = null
        pendingOnDone = null
        pendingOnError = null
        tts?.stop()
        releaseActiveRouting()
        tts?.shutdown()
        tts = null
        updateState { SpeechOutputState() }
    }

    fun currentState(): SpeechOutputState = state

    private fun resolveLocale(tts: TextToSpeech?): Locale {
        val target = preferredLocale
        return if (target != null && tts?.isLanguageAvailable(target) == TextToSpeech.LANG_AVAILABLE) {
            target
        } else {
            Locale.getDefault()
        }
    }

    private fun updateState(transform: (SpeechOutputState) -> SpeechOutputState) {
        state = transform(state)
        onStateChanged(state)
    }

    private fun applyActiveRouting() {
        if (preferredOutputDeviceId == null) return

        val device = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.id == preferredOutputDeviceId } ?: return

        Log.i(TAG, "Applying active TTS routing to device: ${device.productName} (type=${device.type})")

        // Force communication mode to ensure routing takes effect
        if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || 
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val result = audioManager.setCommunicationDevice(device)
            Log.d(TAG, "setCommunicationDevice result: $result")
        } else {
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                audioManager.isSpeakerphoneOn = true
            }
        }
    }

    private fun releaseActiveRouting() {
        if (preferredOutputDeviceId == null) return

        Log.d(TAG, "Releasing active TTS routing")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun logSpeechOutputEnvironment(phase: String) {
        val selectedLabel = audioRoutingRepository.resolveSelectedOutputLabel(preferredOutputDeviceId)
            ?: "System default"
        val communicationOutput = audioRoutingRepository.resolveCommunicationOutputLabel() ?: "none"
        val availableOutputs = audioRoutingRepository.describeAvailableOutputDevices()
        val audioManagerState = audioRoutingRepository.describeAudioManagerState()
        Log.i(
            TAG,
            "Speech output $phase: selected=$selectedLabel selectedId=${preferredOutputDeviceId ?: "default"} " +
                "communication=$communicationOutput available=[$availableOutputs] " +
                "audioManagerState=[$audioManagerState]"
        )
    }
}

@Composable
internal fun rememberSpeechOutputController(
    preferredLocale: Locale? = null,
    preferredOutputDeviceId: Int? = null
): Pair<SpeechOutputController, SpeechOutputState> {
    val context = LocalContext.current
    var state by remember { mutableStateOf(SpeechOutputState()) }
    val audioIOManager = remember(context) { AudioIOManager(context.applicationContext) }
    val controller = remember(audioIOManager, preferredLocale, preferredOutputDeviceId) {
        audioIOManager.createSpeechOutputDriver(
            preferredLocale = preferredLocale,
            preferredOutputDeviceId = preferredOutputDeviceId,
            onStateChanged = { updatedState -> state = updatedState }
        )
    }

    DisposableEffect(controller) {
        controller.initialize()
        onDispose {
            controller.dispose()
        }
    }

    return controller to state
}
