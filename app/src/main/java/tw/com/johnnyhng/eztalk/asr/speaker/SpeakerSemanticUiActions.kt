package tw.com.johnnyhng.eztalk.asr.speaker

import android.content.Context
import android.widget.Toast
import tw.com.johnnyhng.eztalk.asr.R

internal fun applySpeakerSemanticDecision(
    context: Context,
    document: SpeakerDocumentUi,
    decision: SpeakerSemanticDecision?,
    contentLines: List<String>,
    playLineWithAsrStop: (SpeakerDocumentUi, Int, String) -> SpeakerPlaybackResult,
    updateCandidateLineIndex: (Int?) -> Unit
): Boolean {
    return when (decision) {
        is SpeakerSemanticDecision.Candidate -> {
            updateCandidateLineIndex(decision.lineIndex)
            Toast.makeText(
                context,
                context.getString(
                    R.string.speaker_semantic_candidate_selected,
                    decision.lineIndex + 1
                ),
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        is SpeakerSemanticDecision.AutoPlay -> {
            updateCandidateLineIndex(decision.lineIndex)
            val lineText = contentLines.getOrNull(decision.lineIndex).orEmpty()
            when (playLineWithAsrStop(document, decision.lineIndex, lineText)) {
                SpeakerPlaybackResult.NOT_READY -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.speaker_tts_not_ready),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                SpeakerPlaybackResult.EMPTY_TEXT -> Unit
                SpeakerPlaybackResult.STARTED -> updateCandidateLineIndex(null)
            }
            true
        }

        else -> false
    }
}
