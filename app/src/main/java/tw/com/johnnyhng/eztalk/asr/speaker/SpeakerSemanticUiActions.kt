package tw.com.johnnyhng.eztalk.asr.speaker

import android.content.Context
import android.widget.Toast
import tw.com.johnnyhng.eztalk.asr.R

internal fun applySpeakerSemanticDecision(
    context: Context,
    document: SpeakerDocumentUi,
    decision: SpeakerSemanticDecision?,
    contentLines: List<String>,
    isSelectedDocumentPlaying: Boolean,
    isSelectedDocumentPaused: Boolean,
    playDocumentWithAsrStop: (SpeakerDocumentUi) -> SpeakerPlaybackResult,
    playLineWithAsrStop: (SpeakerDocumentUi, Int, String) -> SpeakerPlaybackResult,
    pauseDocument: (String) -> Unit,
    stopPlayback: () -> Unit,
    updateCandidateLineIndex: (Int?) -> Unit
): Boolean {
    return when (decision) {
        is SpeakerSemanticDecision.Command -> {
            when (decision.command) {
                SpeakerContentCommand.Play -> {
                    when (playDocumentWithAsrStop(document)) {
                        SpeakerPlaybackResult.NOT_READY -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.speaker_tts_not_ready),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        SpeakerPlaybackResult.EMPTY_TEXT -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.speaker_empty_text_file),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        SpeakerPlaybackResult.STARTED -> updateCandidateLineIndex(null)
                    }
                    true
                }

                SpeakerContentCommand.Pause -> {
                    if (isSelectedDocumentPlaying) {
                        pauseDocument(document.id)
                        true
                    } else {
                        false
                    }
                }

                SpeakerContentCommand.Stop -> {
                    if (isSelectedDocumentPlaying || isSelectedDocumentPaused) {
                        stopPlayback()
                        true
                    } else {
                        false
                    }
                }

                is SpeakerContentCommand.PlayLine -> false
            }
        }

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
