package tw.com.johnnyhng.eztalk.asr.ui.speaker

import android.content.Context
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerLlmFallbackState

internal fun SpeakerLlmFallbackState?.toDisplayText(context: Context): String? {
    return when (this) {
        is SpeakerLlmFallbackState.PreviewReady -> context.getString(
            R.string.speaker_llm_preview_status,
            model,
            candidateCount
        )
        is SpeakerLlmFallbackState.Success -> context.getString(
            R.string.speaker_llm_fallback_success,
            decision.javaClass.simpleName
        )
        is SpeakerLlmFallbackState.Failure -> context.getString(
            R.string.speaker_llm_fallback_failed,
            message
        )
        SpeakerLlmFallbackState.Unavailable -> context.getString(R.string.speaker_llm_preview_unavailable)
        null -> null
    }
}
