package tw.com.johnnyhng.eztalk.asr.speaker

import android.content.Context
import android.util.Log
import android.widget.Toast
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.TAG

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
                            Log.i(TAG, "Speaker decision applied source=${decision.source} action=Play result=NOT_READY")
                            Toast.makeText(
                                context,
                                context.getString(R.string.speaker_tts_not_ready),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        SpeakerPlaybackResult.EMPTY_TEXT -> {
                            Log.i(TAG, "Speaker decision applied source=${decision.source} action=Play result=EMPTY_TEXT")
                            Toast.makeText(
                                context,
                                context.getString(R.string.speaker_empty_text_file),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        SpeakerPlaybackResult.STARTED -> {
                            Log.i(TAG, "Speaker decision applied source=${decision.source} action=Play result=STARTED")
                            updateCandidateLineIndex(null)
                        }
                    }
                    true
                }

                SpeakerContentCommand.Pause -> {
                    if (isSelectedDocumentPlaying) {
                        Log.i(TAG, "Speaker decision applied source=${decision.source} action=Pause result=STARTED")
                        pauseDocument(document.id)
                        true
                    } else {
                        Log.i(TAG, "Speaker decision ignored source=${decision.source} action=Pause reason=not_playing")
                        false
                    }
                }

                SpeakerContentCommand.Stop -> {
                    if (isSelectedDocumentPlaying || isSelectedDocumentPaused) {
                        Log.i(TAG, "Speaker decision applied source=${decision.source} action=Stop result=STARTED")
                        stopPlayback()
                        true
                    } else {
                        Log.i(TAG, "Speaker decision ignored source=${decision.source} action=Stop reason=not_playing")
                        false
                    }
                }

                is SpeakerContentCommand.PlayLine -> false
            }
        }

        is SpeakerSemanticDecision.Candidate -> {
            Log.i(TAG, "Speaker decision applied source=${decision.source} action=Candidate line=${decision.lineIndex}")
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
                    Log.i(TAG, "Speaker decision applied source=${decision.source} action=AutoPlay line=${decision.lineIndex} result=NOT_READY")
                    Toast.makeText(
                        context,
                        context.getString(R.string.speaker_tts_not_ready),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                SpeakerPlaybackResult.EMPTY_TEXT -> {
                    Log.i(TAG, "Speaker decision applied source=${decision.source} action=AutoPlay line=${decision.lineIndex} result=EMPTY_TEXT")
                }

                SpeakerPlaybackResult.STARTED -> {
                    Log.i(TAG, "Speaker decision applied source=${decision.source} action=AutoPlay line=${decision.lineIndex} result=STARTED")
                    updateCandidateLineIndex(null)
                }
            }
            true
        }

        else -> false
    }
}
