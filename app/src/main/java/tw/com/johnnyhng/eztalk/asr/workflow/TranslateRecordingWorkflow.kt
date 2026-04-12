package tw.com.johnnyhng.eztalk.asr.workflow

import kotlin.math.max
import kotlin.math.min

internal data class TranslateCaptureState(
    val fullRecordingBuffer: ArrayList<Float> = arrayListOf(),
    var speechStartOffset: Int = -1,
    var lastSpeechDetectedOffset: Int = -1,
    var isSpeechStarted: Boolean = false,
    var lastRealtimeRecognitionTime: Long = 0L
)

internal fun appendTranslateSamples(
    state: TranslateCaptureState,
    samples: FloatArray,
    keepSamples: Int,
    speechDetected: Boolean
) {
    val currentBufferPosition = state.fullRecordingBuffer.size
    state.fullRecordingBuffer.addAll(samples.toList())

    if (!state.isSpeechStarted && speechDetected) {
        state.isSpeechStarted = true
        state.speechStartOffset = max(0, currentBufferPosition - keepSamples)
    }
}

internal fun markTranslateVadSegmentDetected(state: TranslateCaptureState) {
    state.lastSpeechDetectedOffset = state.fullRecordingBuffer.size
}

internal fun buildTranslateFinalAudio(
    state: TranslateCaptureState,
    keepSamples: Int
): FloatArray {
    if (!state.isSpeechStarted || state.speechStartOffset == -1) {
        return FloatArray(0)
    }

    val endPosition = if (state.lastSpeechDetectedOffset != -1) {
        min(state.fullRecordingBuffer.size, state.lastSpeechDetectedOffset + keepSamples)
    } else {
        state.fullRecordingBuffer.size
    }

    if (state.speechStartOffset >= endPosition) {
        return FloatArray(0)
    }

    return state.fullRecordingBuffer
        .subList(state.speechStartOffset, endPosition)
        .toFloatArray()
}
