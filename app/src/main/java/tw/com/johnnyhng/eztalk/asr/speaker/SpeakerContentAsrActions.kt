package tw.com.johnnyhng.eztalk.asr.speaker

import android.content.Context
import android.util.Log
import android.widget.Toast
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.TAG

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
    val command = resolveSpeakerContentCommand(finalText, contentLines)
    Log.i(
        TAG,
        "Speaker content command resolve text=$finalText command=$command isPlaying=$isSelectedDocumentPlaying isPaused=$isSelectedDocumentPaused"
    )
    return when (command) {
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
            } else {
                Log.i(TAG, "Speaker content command Pause ignored because document is not currently playing")
            }
            true
        }

        SpeakerContentCommand.Stop -> {
            resetContentSemanticUi()
            Log.i(TAG, "Speaker content command matched: Stop")
            if (isSelectedDocumentPlaying || isSelectedDocumentPaused) {
                stopPlayback()
            } else {
                Log.i(TAG, "Speaker content command Stop ignored because document is neither playing nor paused")
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

        null -> {
            Log.i(TAG, "Speaker content command not matched; continuing to semantic resolution")
            false
        }
    }
}

internal suspend fun handleSpeakerContentAsr(
    context: Context,
    semanticModule: SpeakerSemanticModule,
    finalText: String,
    document: SpeakerDocumentUi,
    contentLines: List<String>,
    indexedChunks: List<SpeakerIndexedChunk>,
    isSelectedDocumentPlaying: Boolean,
    isSelectedDocumentPaused: Boolean,
    isLlmFallbackEnabled: Boolean,
    resetContentSemanticUi: () -> Unit,
    pauseDocument: (String) -> Unit,
    stopPlayback: () -> Unit,
    playDocumentWithAsrStop: (SpeakerDocumentUi) -> SpeakerPlaybackResult,
    playLineWithAsrStop: (SpeakerDocumentUi, Int, String) -> SpeakerPlaybackResult,
    updateCandidateLineIndex: (Int?) -> Unit,
    updateLlmFallbackState: (SpeakerLlmFallbackState?) -> Unit
) {
    if (
        handleSpeakerContentCommand(
            context = context,
            finalText = finalText,
            document = document,
            contentLines = contentLines,
            isSelectedDocumentPlaying = isSelectedDocumentPlaying,
            isSelectedDocumentPaused = isSelectedDocumentPaused,
            resetContentSemanticUi = resetContentSemanticUi,
            pauseDocument = pauseDocument,
            stopPlayback = stopPlayback,
            playDocumentWithAsrStop = playDocumentWithAsrStop,
            playLineWithAsrStop = playLineWithAsrStop
        )
    ) {
        return
    }

    handleSpeakerSemanticResolution(
        context = context,
        semanticModule = semanticModule,
        queryText = finalText,
        document = document,
        contentLines = contentLines,
        indexedChunks = indexedChunks,
        isLlmFallbackEnabled = isLlmFallbackEnabled,
        playLineWithAsrStop = playLineWithAsrStop,
        updateCandidateLineIndex = updateCandidateLineIndex,
        updateLlmFallbackState = updateLlmFallbackState
    )
}

internal suspend fun handleSpeakerSemanticResolution(
    context: Context,
    semanticModule: SpeakerSemanticModule,
    queryText: String,
    document: SpeakerDocumentUi,
    contentLines: List<String>,
    indexedChunks: List<SpeakerIndexedChunk>,
    isLlmFallbackEnabled: Boolean,
    playLineWithAsrStop: (SpeakerDocumentUi, Int, String) -> SpeakerPlaybackResult,
    updateCandidateLineIndex: (Int?) -> Unit,
    updateLlmFallbackState: (SpeakerLlmFallbackState?) -> Unit
): Boolean {
    val resolution = semanticModule.resolve(
        queryText = queryText,
        lines = contentLines,
        chunks = indexedChunks
    )
    Log.i(
        TAG,
        "Speaker semantic query embedding length=${resolution.query.embedding.size} preview=${resolution.query.embedding.previewForLog()}"
    )
    Log.i(
        TAG,
        "Speaker semantic top3 cosine=${resolution.rankedResults.take(3).formatTop3CosineForLog()}"
    )
    return when (val decision = resolution.decision) {
        SpeakerSemanticDecision.NoMatch -> {
            updateCandidateLineIndex(null)
            Log.i(TAG, "Speaker semantic no matched content")
            val noMatchOutcome = semanticModule.resolveNoMatchOutcome(
                queryText = queryText,
                rankedResults = resolution.rankedResults,
                lines = contentLines,
                isLlmFallbackEnabled = isLlmFallbackEnabled
            )
            updateLlmFallbackState(
                noMatchOutcome.toFallbackState(
                    fallbackMessage = context.getString(R.string.speaker_llm_preview_unavailable),
                    onFailure = { error -> Log.w(TAG, "Speaker LLM fallback failed", error) }
                )
            )
            if (
                applySpeakerSemanticDecision(
                    context = context,
                    document = document,
                    decision = noMatchOutcome.llmFallbackResult?.getOrNull(),
                    contentLines = contentLines,
                    playLineWithAsrStop = playLineWithAsrStop,
                    updateCandidateLineIndex = updateCandidateLineIndex
                )
            ) {
                true
            } else {
                Toast.makeText(
                    context,
                    context.getString(noMatchOutcome.toastMessageResId()),
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
        }

        is SpeakerSemanticDecision.Candidate -> {
            updateCandidateLineIndex(decision.lineIndex)
            updateLlmFallbackState(null)
            logSpeakerSemanticDecision("candidate", decision)
            applySpeakerSemanticDecision(
                context = context,
                document = document,
                decision = decision,
                contentLines = contentLines,
                playLineWithAsrStop = playLineWithAsrStop,
                updateCandidateLineIndex = updateCandidateLineIndex
            )
        }

        is SpeakerSemanticDecision.AutoPlay -> {
            updateLlmFallbackState(null)
            logSpeakerSemanticDecision("autoplay", decision)
            applySpeakerSemanticDecision(
                context = context,
                document = document,
                decision = decision,
                contentLines = contentLines,
                playLineWithAsrStop = playLineWithAsrStop,
                updateCandidateLineIndex = updateCandidateLineIndex
            )
        }

        is SpeakerSemanticDecision.Ambiguous -> false
    }
}

private fun logSpeakerSemanticDecision(
    label: String,
    decision: SpeakerSemanticDecision
) {
    val result = when (decision) {
        is SpeakerSemanticDecision.Candidate -> decision.result
        is SpeakerSemanticDecision.AutoPlay -> decision.result
        else -> return
    }
    val lineIndex = when (decision) {
        is SpeakerSemanticDecision.Candidate -> decision.lineIndex
        is SpeakerSemanticDecision.AutoPlay -> decision.lineIndex
        else -> return
    }
    Log.i(
        TAG,
        "Speaker semantic $label line=$lineIndex score=${"%.4f".format(result.finalScore)} semantic=${"%.4f".format(result.semanticScore)} lexical=${"%.4f".format(result.lexicalScore)} lines=${result.lineStart}-${result.lineEnd} text=${result.matchedText.oneLineForLog()}"
    )
}
