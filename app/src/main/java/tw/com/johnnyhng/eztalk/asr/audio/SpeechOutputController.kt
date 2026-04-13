package tw.com.johnnyhng.eztalk.asr.audio

import android.content.Context
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

internal class SpeechOutputController(
    private val context: Context,
    private val preferredLocale: Locale?,
    private val onStateChanged: (SpeechOutputState) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var state = SpeechOutputState()
    private var pendingOnDone: (() -> Unit)? = null

    fun initialize() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            val isReady = if (status == TextToSpeech.SUCCESS) {
                tts?.language = resolveLocale(tts)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        updateState { it.copy(isSpeaking = true) }
                    }

                    override fun onDone(utteranceId: String?) {
                        updateState { it.copy(isSpeaking = false) }
                        pendingOnDone?.also { callback ->
                            pendingOnDone = null
                            callback()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        updateState { it.copy(isSpeaking = false) }
                        pendingOnDone = null
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

    fun speak(text: String, onDone: (() -> Unit)? = null): Boolean {
        val engine = tts ?: return false
        pendingOnDone = onDone
        val utteranceId = UUID.randomUUID().toString()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            pendingOnDone = null
            return false
        }
        return true
    }

    fun stop() {
        pendingOnDone = null
        tts?.stop()
        updateState { it.copy(isSpeaking = false) }
    }

    fun dispose() {
        pendingOnDone = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        updateState { SpeechOutputState() }
    }

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
}

@Composable
internal fun rememberSpeechOutputController(
    preferredLocale: Locale? = null
): Pair<SpeechOutputController, SpeechOutputState> {
    val context = LocalContext.current
    var state by remember { mutableStateOf(SpeechOutputState()) }
    val controller = remember(context, preferredLocale) {
        SpeechOutputController(
            context = context,
            preferredLocale = preferredLocale,
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
