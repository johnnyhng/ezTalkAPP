package tw.com.johnnyhng.eztalk.asr.ui.speaker

import android.content.Context
import android.util.Log
import android.widget.Toast
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerContentCommand
import tw.com.johnnyhng.eztalk.asr.speaker.SpeakerPlaybackResult
import tw.com.johnnyhng.eztalk.asr.speaker.resolveSpeakerContentCommand

internal fun handleSpeakerContentCommand(
    context: Context,
    finalText: String,
    document: SpeakerDocumentUi,
    contentLines: List<String>,
    isSelectedDocumentPlaying: Boolean,
    isSelectedDocumentPaused: Boolean,
    resetContentSemanticUi: () -> Unit,
    pauseDocument: (String) -> Unit,
    stopPlayback: () -> Unit,
    playDocumentWithAsrStop: (SpeakerDocumentUi) -> SpeakerPlaybackResult,
    playLineWithAsrStop: (SpeakerDocumentUi, Int, String) -> SpeakerPlaybackResult
): Boolean {
    return when (val command = resolveSpeakerContentCommand(finalText, contentLines)) {
        SpeakerContentCommand.Play -> {
            resetContentSemanticUi()
            Log.i(TAG, "Speaker content command matched: Play")
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

                SpeakerPlaybackResult.STARTED -> Unit
            }
            true
        }

        SpeakerContentCommand.Pause -> {
            resetContentSemanticUi()
            Log.i(TAG, "Speaker content command matched: Pause")
            if (isSelectedDocumentPlaying) {
                pauseDocument(document.id)
            }
            true
        }

        SpeakerContentCommand.Stop -> {
            resetContentSemanticUi()
            Log.i(TAG, "Speaker content command matched: Stop")
            if (isSelectedDocumentPlaying || isSelectedDocumentPaused) {
                stopPlayback()
            }
            true
        }

        is SpeakerContentCommand.PlayLine -> {
            resetContentSemanticUi()
            Log.i(TAG, "Speaker content command matched: PlayLine(${command.lineIndex})")
            val lineText = contentLines.getOrNull(command.lineIndex).orEmpty()
            when (playLineWithAsrStop(document, command.lineIndex, lineText)) {
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

                SpeakerPlaybackResult.STARTED -> Unit
            }
            true
        }

        null -> false
    }
}
