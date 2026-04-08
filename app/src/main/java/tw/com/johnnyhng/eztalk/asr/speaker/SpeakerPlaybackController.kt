package tw.com.johnnyhng.eztalk.asr.speaker

import tw.com.johnnyhng.eztalk.asr.ui.speaker.SpeakerDocumentUi

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.util.Locale

internal enum class SpeakerPlaybackResult {
    STARTED,
    NOT_READY,
    EMPTY_TEXT
}

internal data class SpeakerPlaybackState(
    val isReady: Boolean = false,
    val currentPlayingDocumentId: String? = null,
    val playbackDocumentId: String? = null,
    val playbackSegments: List<String> = emptyList(),
    val playbackLineIndexes: List<Int> = emptyList(),
    val playbackSegmentIndex: Int = 0,
    val currentPlayingLineIndex: Int? = null,
    val isPlaybackPaused: Boolean = false
)

internal class SpeakerPlaybackController(
    private val context: Context,
    private val onStateChanged: (SpeakerPlaybackState) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var state = SpeakerPlaybackState()

    fun initialize(scope: kotlinx.coroutines.CoroutineScope) {
        tts = TextToSpeech(context) { status ->
            val isReady = if (status == TextToSpeech.SUCCESS) {
                tts?.language = if (
                    tts?.isLanguageAvailable(Locale.TRADITIONAL_CHINESE) == TextToSpeech.LANG_AVAILABLE
                ) {
                    Locale.TRADITIONAL_CHINESE
                } else {
                    Locale.getDefault()
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val parsed = utteranceId?.let(::parseSpeakerUtteranceId) ?: return
                        scope.launch {
                            updateState {
                                it.copy(
                                    playbackDocumentId = parsed.documentId,
                                    playbackSegmentIndex = parsed.segmentIndex,
                                    currentPlayingLineIndex = it.playbackLineIndexes.getOrNull(parsed.segmentIndex),
                                    currentPlayingDocumentId = parsed.documentId
                                )
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        val parsed = utteranceId?.let(::parseSpeakerUtteranceId) ?: return
                        scope.launch {
                            if (state.playbackDocumentId != parsed.documentId || state.isPlaybackPaused) {
                                return@launch
                            }
                            val nextIndex = parsed.segmentIndex + 1
                            if (nextIndex < state.playbackSegments.size) {
                                speakSegment(parsed.documentId, nextIndex)
                            } else {
                                reset()
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        val parsed = utteranceId?.let(::parseSpeakerUtteranceId) ?: return
                        scope.launch {
                            if (state.playbackDocumentId == parsed.documentId) {
                                reset()
                            }
                        }
                    }
                })
                true
            } else {
                false
            }
            updateState { it.copy(isReady = isReady) }
        }
    }

    fun dispose() {
        tts?.stop()
        tts?.shutdown()
        reset()
    }

    fun isPlayingDocument(documentId: String?): Boolean {
        return documentId != null && documentId == state.currentPlayingDocumentId
    }

    fun isPausedDocument(documentId: String?): Boolean {
        return documentId != null &&
            documentId == state.playbackDocumentId &&
            state.isPlaybackPaused
    }

    fun playDocument(document: SpeakerDocumentUi): SpeakerPlaybackResult {
        if (!state.isReady) return SpeakerPlaybackResult.NOT_READY
        if (document.fullText.isBlank()) return SpeakerPlaybackResult.EMPTY_TEXT

        tts?.stop()
        if (isPausedDocument(document.id) && state.playbackSegments.isNotEmpty()) {
            speakSegment(document.id, state.playbackSegmentIndex)
            return SpeakerPlaybackResult.STARTED
        }

        val playbackPlan = buildSpeakerPlaybackPlan(document.fullText)
        if (playbackPlan.segments.isEmpty()) return SpeakerPlaybackResult.EMPTY_TEXT

        updateState {
            it.copy(
                playbackSegments = playbackPlan.segments,
                playbackLineIndexes = playbackPlan.lineIndexes,
                playbackSegmentIndex = 0,
                currentPlayingLineIndex = null,
                isPlaybackPaused = false
            )
        }
        speakSegment(document.id, 0)
        return SpeakerPlaybackResult.STARTED
    }

    fun playLine(document: SpeakerDocumentUi, lineIndex: Int, line: String): SpeakerPlaybackResult {
        if (!state.isReady) return SpeakerPlaybackResult.NOT_READY
        if (line.isBlank()) return SpeakerPlaybackResult.EMPTY_TEXT

        tts?.stop()
        updateState {
            it.copy(
                playbackSegments = listOf(line),
                playbackLineIndexes = listOf(lineIndex),
                playbackSegmentIndex = 0,
                currentPlayingLineIndex = null,
                isPlaybackPaused = false
            )
        }
        speakSegment(document.id, 0)
        return SpeakerPlaybackResult.STARTED
    }

    fun pause(documentId: String?) {
        if (!isPlayingDocument(documentId)) return
        tts?.stop()
        updateState {
            it.copy(
                currentPlayingDocumentId = null,
                isPlaybackPaused = true
            )
        }
    }

    fun stop() {
        tts?.stop()
        reset()
    }

    fun stopIfPlaying(documentId: String?) {
        if (documentId == null) return
        if (documentId == state.playbackDocumentId || documentId == state.currentPlayingDocumentId) {
            stop()
        }
    }

    fun currentState(): SpeakerPlaybackState = state

    private fun speakSegment(documentId: String, segmentIndex: Int) {
        val segment = state.playbackSegments.getOrNull(segmentIndex)
        if (segment == null) {
            reset()
            return
        }
        updateState {
            it.copy(
                playbackDocumentId = documentId,
                playbackSegmentIndex = segmentIndex,
                currentPlayingLineIndex = it.playbackLineIndexes.getOrNull(segmentIndex),
                isPlaybackPaused = false,
                currentPlayingDocumentId = documentId
            )
        }
        tts?.speak(
            segment,
            TextToSpeech.QUEUE_FLUSH,
            null,
            buildSpeakerUtteranceId(documentId, segmentIndex)
        )
    }

    private fun reset() {
        updateState {
            it.copy(
                currentPlayingDocumentId = null,
                playbackDocumentId = null,
                playbackSegments = emptyList(),
                playbackLineIndexes = emptyList(),
                playbackSegmentIndex = 0,
                currentPlayingLineIndex = null,
                isPlaybackPaused = false
            )
        }
    }

    private fun updateState(transform: (SpeakerPlaybackState) -> SpeakerPlaybackState) {
        state = transform(state)
        onStateChanged(state)
    }
}

@Composable
internal fun rememberSpeakerPlaybackController(): Pair<SpeakerPlaybackController, SpeakerPlaybackState> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SpeakerPlaybackState()) }
    val controller = remember {
        SpeakerPlaybackController(context) { updatedState ->
            state = updatedState
        }
    }

    DisposableEffect(controller) {
        controller.initialize(scope)
        onDispose {
            controller.dispose()
        }
    }

    return controller to state
}

private data class SpeakerUtteranceId(
    val documentId: String,
    val segmentIndex: Int
)

private data class SpeakerPlaybackPlan(
    val segments: List<String>,
    val lineIndexes: List<Int>
)

private fun buildSpeakerUtteranceId(documentId: String, segmentIndex: Int): String {
    return "$segmentIndex::$documentId"
}

private fun parseSpeakerUtteranceId(utteranceId: String): SpeakerUtteranceId? {
    val separatorIndex = utteranceId.indexOf("::")
    if (separatorIndex <= 0) return null
    val segmentIndex = utteranceId.substring(0, separatorIndex).toIntOrNull() ?: return null
    val documentId = utteranceId.substring(separatorIndex + 2)
    if (documentId.isBlank()) return null
    return SpeakerUtteranceId(documentId = documentId, segmentIndex = segmentIndex)
}

private fun segmentTextForTts(text: String): List<String> {
    val normalized = text
        .replace("\r\n", "\n")
        .trim()
    if (normalized.isBlank()) return emptyList()

    return normalized
        .split('\n')
        .flatMap { paragraph ->
            paragraph
                .split(Regex("(?<=[。！？!?；;])"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
        .flatMap { chunkTextForTts(it) }
}

private fun buildSpeakerPlaybackPlan(text: String): SpeakerPlaybackPlan {
    val lines = text.replace("\r\n", "\n").split('\n')
    val segments = mutableListOf<String>()
    val lineIndexes = mutableListOf<Int>()

    lines.forEachIndexed { lineIndex, line ->
        val lineSegments = segmentTextForTts(line)
        lineSegments.forEach { segment ->
            segments += segment
            lineIndexes += lineIndex
        }
    }

    return SpeakerPlaybackPlan(segments = segments, lineIndexes = lineIndexes)
}

private fun chunkTextForTts(text: String, maxLength: Int = 180): List<String> {
    if (text.length <= maxLength) return listOf(text)

    val chunks = mutableListOf<String>()
    var remaining = text.trim()
    while (remaining.length > maxLength) {
        val splitIndex = remaining.lastIndexOf(' ', startIndex = maxLength)
            .takeIf { it > maxLength / 2 }
            ?: maxLength
        chunks += remaining.substring(0, splitIndex).trim()
        remaining = remaining.substring(splitIndex).trim()
    }
    if (remaining.isNotBlank()) {
        chunks += remaining
    }
    return chunks
}
