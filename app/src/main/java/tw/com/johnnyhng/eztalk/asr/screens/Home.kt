package tw.com.johnnyhng.eztalk.asr.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.SimulateStreamingAsr
import tw.com.johnnyhng.eztalk.asr.audio.rememberSpeechOutputController
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript
import tw.com.johnnyhng.eztalk.asr.llm.TranscriptCorrectionModule
import tw.com.johnnyhng.eztalk.asr.llm.TranscriptEnglishTranslationModule
import tw.com.johnnyhng.eztalk.asr.llm.TranscriptCorrectionProviderFactory
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.utils.*
import tw.com.johnnyhng.eztalk.asr.workflow.reduceTranscriptAfterConfirmation
import tw.com.johnnyhng.eztalk.asr.workflow.shouldAttemptFeedback
import tw.com.johnnyhng.eztalk.asr.widgets.*
import java.io.File
import java.util.*

private fun logHomeJsonlUpdate(reason: String, transcript: Transcript) {
    Log.d(
        TAG,
        "Home jsonl update: reason=$reason, file=${File(transcript.wavFilePath).name}, modified=${transcript.modifiedText}, checked=${transcript.checked}, mutable=${transcript.mutable}, localCandidates=${transcript.localCandidates.size}, remoteCandidates=${transcript.remoteCandidates.size}, utteranceVariants=${transcript.utteranceVariants.size}:${transcript.utteranceVariants}"
    )
}

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = context as Activity
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // UI state tied to ViewModel
    val isStarted by homeViewModel.isRecording.collectAsState()
    val latestAudioSamples by homeViewModel.latestSamples.collectAsState()
    val isRecognizingSpeech by homeViewModel.isRecognizingSpeech.collectAsState()
    val countdownProgress by homeViewModel.countdownProgress.collectAsState()
    val userSettings by homeViewModel.userSettings.collectAsState()
    val selectedModel by homeViewModel.selectedModelFlow.collectAsState()
    val isAsrModelLoading by homeViewModel.isAsrModelLoading.collectAsState()
    
    val resultList = remember { mutableStateListOf<Transcript>() }
    val lazyColumnListState = rememberLazyListState()

    // Local UI states
    var isInlineEditing by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var editingText by remember { mutableStateOf("") }
    var localCandidate by remember { mutableStateOf<String?>(null) }
    var isFetchingCandidates by remember { mutableStateOf(false) }
    var transcriptToEditInDialog by remember { mutableStateOf<Pair<Int, Transcript>?>(null) }
    val isEditing = isInlineEditing || transcriptToEditInDialog != null
    val fetchingJobs = remember { mutableStateMapOf<String, Job>() }
    val correctionJobs = remember { mutableMapOf<String, Job>() }
    val correctionRunning = remember { mutableStateMapOf<String, Boolean>() }
    val translationJobs = remember { mutableMapOf<String, Job>() }

    // TTS and Background Logic
    val currentlyPlaying by MediaController.currentlyPlaying.collectAsState()
    val (speechController, speechState) = rememberSpeechOutputController(
        preferredLocale = Locale.TRADITIONAL_CHINESE,
        preferredOutputDeviceId = userSettings.preferredAudioOutputDeviceId
    )
    val (englishSpeechController, englishSpeechState) = rememberSpeechOutputController(
        preferredLocale = Locale.US,
        preferredOutputDeviceId = userSettings.preferredAudioOutputDeviceId
    )
    val isTtsSpeaking = speechState.isSpeaking
    val isEnglishTtsSpeaking = englishSpeechState.isSpeaking
    val isAnyTtsSpeaking = isTtsSpeaking || isEnglishTtsSpeaking
    val recognitionQueue = remember { Channel<String>(Channel.UNLIMITED) }
    val correctionProviderFactory = remember(appContext) {
        TranscriptCorrectionProviderFactory(appContext)
    }
    val correctionProvider = remember(userSettings.geminiModel, correctionProviderFactory) {
        correctionProviderFactory.create(userSettings.geminiModel)
    }
    val transcriptCorrectionModule = remember(correctionProvider, userSettings.geminiModel) {
        TranscriptCorrectionModule(
            llmProvider = correctionProvider,
            llmModel = userSettings.geminiModel
        )
    }
    val transcriptEnglishTranslationModule = remember(correctionProvider, userSettings.geminiModel) {
        TranscriptEnglishTranslationModule(
            llmProvider = correctionProvider,
            llmModel = userSettings.geminiModel
        )
    }

    fun clearCorrectionState(wavPath: String) {
        correctionJobs.remove(wavPath)?.cancel()
        correctionRunning.remove(wavPath)
    }

    fun clearTranslationState(wavPath: String) {
        translationJobs.remove(wavPath)?.cancel()
    }

    fun launchBackgroundEnglishTranslation(transcript: Transcript) {
        val wavPath = transcript.wavFilePath
        val expectedSourceText = transcript.modifiedText.trim()
        if (!userSettings.enableHomeEnglishTranslation || wavPath.isBlank() || expectedSourceText.isBlank()) {
            return
        }

        clearTranslationState(wavPath)
        val job = coroutineScope.launch {
            try {
                val translatedText = withContext(Dispatchers.IO) {
                    transcriptEnglishTranslationModule.translate(expectedSourceText).getOrElse { error ->
                        Log.w(TAG, "Home English translation failed", error)
                        null
                    }
                } ?: return@launch

                val updatedTranscript = withContext(Dispatchers.Main) {
                    val index = resultList.indexOfFirst { it.wavFilePath == wavPath }
                    if (index == -1) {
                        null
                    } else {
                        val current = resultList[index]
                        if (current.modifiedText.trim() != expectedSourceText ||
                            translatedText == current.englishTranslation
                        ) {
                            null
                        } else {
                            current.copy(englishTranslation = translatedText).also {
                                resultList[index] = it
                            }
                        }
                    }
                }

                updatedTranscript?.let {
                    withContext(Dispatchers.IO) {
                        logHomeJsonlUpdate("english_translation_applied", it)
                        persistTranscriptJsonl(context, userSettings.userId, it)
                    }
                }
            } finally {
                translationJobs.remove(wavPath)
            }
        }
        translationJobs[wavPath] = job
    }

    fun updateTranscriptAndRefreshEnglish(
        index: Int,
        updatedTranscript: Transcript,
        previousModifiedText: String? = null
    ) {
        val normalizedTranscript = if (
            userSettings.enableHomeEnglishTranslation &&
            previousModifiedText != null &&
            previousModifiedText != updatedTranscript.modifiedText
        ) {
            updatedTranscript.copy(englishTranslation = "")
        } else {
            updatedTranscript
        }
        resultList[index] = normalizedTranscript
        if (userSettings.enableHomeEnglishTranslation &&
            normalizedTranscript.wavFilePath.isNotBlank() &&
            normalizedTranscript.modifiedText.isNotBlank()
        ) {
            launchBackgroundEnglishTranslation(normalizedTranscript)
        }
    }

    fun launchBackgroundCorrection(transcript: Transcript) {
        val wavPath = transcript.wavFilePath
        if (!userSettings.enableHomeLlmCorrection || wavPath.isBlank()) return

        clearCorrectionState(wavPath)
        correctionRunning[wavPath] = true
        val expectedModifiedText = transcript.modifiedText

        val job = coroutineScope.launch {
            try {
                val contextLines = resultList
                    .filter { it.wavFilePath != wavPath }
                    .takeLast(5)
                    .map { it.modifiedText }
                    .filter { it.isNotBlank() }
                val correction = withContext(Dispatchers.IO) {
                    transcriptCorrectionModule.correct(
                        utteranceVariants = transcript.utteranceVariants.ifEmpty {
                            listOf(transcript.recognizedText)
                        },
                        contextLines = contextLines
                    ).getOrElse { error ->
                        Log.w(TAG, "Home LLM correction failed", error)
                        null
                        }
                }

                val updatedTranscript = withContext(Dispatchers.Main) {
                    val index = resultList.indexOfFirst { it.wavFilePath == wavPath }
                    if (index == -1) {
                        null
                    } else {
                        val current = resultList[index]
                        if (!current.mutable || current.modifiedText != expectedModifiedText) {
                            null
                        } else if (correction == null || correction.correctedText == current.modifiedText) {
                            current
                        } else {
                            current.copy(
                                modifiedText = correction.correctedText,
                                englishTranslation = ""
                            ).also {
                                resultList[index] = it
                            }
                        }
                    }
                }

                if (updatedTranscript != null && updatedTranscript.modifiedText != expectedModifiedText) {
                    launchBackgroundEnglishTranslation(updatedTranscript)
                    withContext(Dispatchers.IO) {
                        logHomeJsonlUpdate("llm_correction_applied", updatedTranscript)
                        persistTranscriptJsonl(context, userSettings.userId, updatedTranscript)
                    }
                }
            } finally {
                correctionJobs.remove(wavPath)
                correctionRunning.remove(wavPath)
            }
        }

        correctionJobs[wavPath] = job
    }

    LaunchedEffect(selectedModel?.name, userSettings.userId, userSettings.mobileModelSha256) {
        if (selectedModel != null) {
            homeViewModel.ensureSelectedModelInitialized()
        }
    }

    // Background recognition queue processor
    LaunchedEffect(
        userSettings.effectiveRecognitionUrl,
        userSettings.userId,
        userSettings.includeRemoteCandidatesInUtteranceVariants
    ) {
        for (wavPath in recognitionQueue) {
            val url = userSettings.effectiveRecognitionUrl
            if (url.isBlank()) continue

            val job = coroutineScope.launch(Dispatchers.IO) {
                val transcript = withContext(Dispatchers.Main) {
                    resultList.find { it.wavFilePath == wavPath }
                }

                transcript?.let {
                    val sentences = getRemoteCandidates(
                        context = context,
                        wavFilePath = wavPath,
                        userId = userSettings.userId,
                        recognitionUrl = url,
                        allowInsecureTls = userSettings.allowInsecureTls,
                        originalText = it.recognizedText,
                        currentText = it.modifiedText
                    )

                    if (sentences.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val index = resultList.indexOfFirst { r -> r.wavFilePath == wavPath }
                            if (index != -1) {
                                val current = resultList[index]
                                val mergedVariants = mergeRemoteCandidatesIntoUtteranceVariants(
                                    utteranceVariants = current.utteranceVariants,
                                    remoteCandidates = sentences,
                                    enabled = userSettings.includeRemoteCandidatesInUtteranceVariants
                                )
                                Log.d(
                                    TAG,
                                    "Home remote candidates merged into variants: file=${File(wavPath).name}, enabled=${userSettings.includeRemoteCandidatesInUtteranceVariants}, remote=${sentences.size}, beforeVariants=${current.utteranceVariants.size}, afterVariants=${mergedVariants.size}, utteranceVariants=$mergedVariants"
                                )
                                val updated = current.copy(
                                    remoteCandidates = sentences,
                                    utteranceVariants = mergedVariants
                                )
                                resultList[index] = updated
                                withContext(Dispatchers.IO) {
                                    logHomeJsonlUpdate("remote_candidates_loaded", updated)
                                    persistTranscriptJsonl(context, userSettings.userId, updated)
                                }
                                if (mergedVariants != current.utteranceVariants) {
                                    Log.d(
                                        TAG,
                                        "Home LLM correction queued after utterance variants update: file=${File(wavPath).name}"
                                    )
                                    launchBackgroundCorrection(updated)
                                }
                            }
                        }
                    }
                } ?: Log.w(TAG, "Could not find transcript for wavPath: $wavPath to update remote candidates.")
            }

            fetchingJobs[wavPath] = job
            job.invokeOnCompletion {
                fetchingJobs.remove(wavPath)
            }
        }
    }

    // Handlers
    fun handleTtsClick(index: Int, text: String) {
        MediaController.stop()
        englishSpeechController.stop()
        speechController.speak(text)
        
        val item = resultList[index]
        val shouldRunFeedback = shouldAttemptFeedback(item, userSettings.enableTtsFeedback)
        if (shouldRunFeedback) {
            coroutineScope.launch {
                fetchingJobs[item.wavFilePath]?.join()
                val success = withContext(Dispatchers.IO) {
                    feedbackToBackend(
                        userSettings.backendUrl,
                        item.wavFilePath,
                        userSettings.userId,
                        allowInsecureTls = userSettings.allowInsecureTls
                    )
                }
                if (success) {
                    val updated = withContext(Dispatchers.Main) {
                        val cIndex = resultList.indexOfFirst { it.wavFilePath == item.wavFilePath }
                        if (cIndex == -1) {
                            null
                        } else {
                            val reduced = reduceTranscriptAfterConfirmation(
                                transcript = resultList[cIndex],
                                newText = text,
                                lockTranscript = true
                            )
                            val normalized = if (userSettings.enableHomeEnglishTranslation &&
                                reduced.modifiedText != resultList[cIndex].modifiedText
                            ) {
                                reduced.copy(englishTranslation = "")
                            } else {
                                reduced
                            }
                            resultList[cIndex] = normalized
                            if (userSettings.enableHomeEnglishTranslation) {
                                launchBackgroundEnglishTranslation(normalized)
                            }
                            normalized
                        }
                    }

                    updated?.let {
                        withContext(Dispatchers.IO) {
                            logHomeJsonlUpdate("tts_feedback_success", it)
                            persistTranscriptJsonl(context, userSettings.userId, it)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.feedback_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            val updated = reduceTranscriptAfterConfirmation(
                transcript = item,
                newText = text,
                lockTranscript = userSettings.enableTtsFeedback
            )
            updateTranscriptAndRefreshEnglish(
                index = index,
                updatedTranscript = updated,
                previousModifiedText = item.modifiedText
            )
            coroutineScope.launch(Dispatchers.IO) {
                val latestTranscript = resultList.getOrNull(index) ?: return@launch
                logHomeJsonlUpdate("tts_without_feedback", latestTranscript)
                persistTranscriptJsonl(context, userSettings.userId, latestTranscript)
            }
        }
    }

    fun handleDialogTtsConfirm(index: Int, text: String) {
        val item = resultList.getOrNull(index) ?: return

        val shouldRunFeedback = shouldAttemptFeedback(item, userSettings.enableTtsFeedback)
        if (shouldRunFeedback) {
            coroutineScope.launch {
                fetchingJobs[item.wavFilePath]?.join()
                val success = withContext(Dispatchers.IO) {
                    feedbackToBackend(
                        userSettings.backendUrl,
                        item.wavFilePath,
                        userSettings.userId,
                        allowInsecureTls = userSettings.allowInsecureTls
                    )
                }

                if (success) {
                    val updatedItem = reduceTranscriptAfterConfirmation(
                        transcript = item,
                        newText = text,
                        lockTranscript = true
                    )
                    updateTranscriptAndRefreshEnglish(
                        index = index,
                        updatedTranscript = updatedItem,
                        previousModifiedText = item.modifiedText
                    )
                    withContext(Dispatchers.IO) {
                        val latestTranscript = resultList.getOrNull(index) ?: return@withContext
                        logHomeJsonlUpdate("dialog_tts_feedback_success", latestTranscript)
                        persistTranscriptJsonl(context, userSettings.userId, latestTranscript)
                    }
                    transcriptToEditInDialog = null
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.feedback_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            val updatedItem = reduceTranscriptAfterConfirmation(
                transcript = item,
                newText = text,
                lockTranscript = userSettings.enableTtsFeedback
            )
            updateTranscriptAndRefreshEnglish(
                index = index,
                updatedTranscript = updatedItem,
                previousModifiedText = item.modifiedText
            )
            coroutineScope.launch(Dispatchers.IO) {
                val latestTranscript = resultList.getOrNull(index) ?: return@launch
                logHomeJsonlUpdate("dialog_tts_without_feedback", latestTranscript)
                persistTranscriptJsonl(context, userSettings.userId, latestTranscript)
            }
            transcriptToEditInDialog = null
        }
    }

    // Connect ViewModel events to resultList
    LaunchedEffect(
        userSettings.enableHomeLlmCorrection,
        userSettings.enableHomeEnglishTranslation,
        userSettings.effectiveRecognitionUrl,
        userSettings.userId
    ) {
        homeViewModel.finalTranscript.collect { transcript ->
            if (transcript.modifiedText.isNotBlank()) {
                if (resultList.isNotEmpty() && resultList.last().wavFilePath.isEmpty()) {
                    resultList[resultList.size - 1] = transcript
                } else {
                    resultList.add(transcript)
                }
                lazyColumnListState.animateScrollToItem(resultList.size - 1)

                if (userSettings.effectiveRecognitionUrl.isNotBlank()) {
                    recognitionQueue.trySend(transcript.wavFilePath)
                }
                launchBackgroundCorrection(transcript)
                launchBackgroundEnglishTranslation(transcript)
            }
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.partialText.collect { text ->
            if (resultList.isEmpty() || resultList.last().wavFilePath.isNotEmpty()) {
                resultList.add(Transcript(recognizedText = text, modifiedText = text, wavFilePath = ""))
            } else {
                resultList[resultList.size - 1] = resultList.last().copy(recognizedText = text, modifiedText = text)
            }
            lazyColumnListState.animateScrollToItem(resultList.size - 1)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechController.stop()
            englishSpeechController.stop()
            recognitionQueue.close()
            correctionJobs.values.forEach { it.cancel() }
            correctionJobs.clear()
            correctionRunning.clear()
            translationJobs.values.forEach { it.cancel() }
            translationJobs.clear()
        }
    }

    LaunchedEffect(userSettings.enableHomeEnglishTranslation) {
        if (!userSettings.enableHomeEnglishTranslation) {
            translationJobs.values.forEach { it.cancel() }
            translationJobs.clear()
        } else {
            resultList
                .filter {
                    it.wavFilePath.isNotBlank() &&
                        it.modifiedText.isNotBlank() &&
                        it.englishTranslation.isBlank()
                }
                .forEach(::launchBackgroundEnglishTranslation)
        }
    }

    LaunchedEffect(userSettings.inlineEdit) {
        if (!userSettings.inlineEdit && isInlineEditing) {
            isInlineEditing = false
            editingIndex = -1
            isFetchingCandidates = false
            localCandidate = null
        }
    }

    // Main UI
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column {
            HomeButtonRow(
                isStarted = isStarted,
                onRecordingButtonClick = {
                    if (!isStarted) {
                        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
                            return@HomeButtonRow
                        }
                        homeViewModel.startTranslateRecording()
                    } else {
                        homeViewModel.toggleRecording()
                    }
                },
                onCopyButtonClick = {
                    if (resultList.isNotEmpty()) {
                        val s = resultList.mapIndexed { i, r -> "${i + 1}: ${r.modifiedText}" }.joinToString("")
                        clipboardManager.setText(AnnotatedString(s))
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                    }
                },
                onClearButtonClick = {
                    resultList
                        .map { it.wavFilePath }
                        .filter { it.isNotBlank() }
                        .forEach {
                            clearCorrectionState(it)
                            clearTranslationState(it)
                        }
                    resultList.clear()
                },
                isPlaybackActive = currentlyPlaying != null || isAnyTtsSpeaking,
                isAsrModelLoading = isAsrModelLoading,
                isEditing = isEditing
            )

            if (isStarted) {
                WaveformDisplay(samples = latestAudioSamples, modifier = Modifier.fillMaxWidth().height(100.dp).padding(vertical = 8.dp))
            }

            CandidateList(
                resultList = resultList,
                lazyListState = lazyColumnListState,
                isInteractionLocked = isEditing,
                isInlineEditing = isInlineEditing,
                editingIndex = editingIndex,
                editingText = editingText,
                onEditingTextChange = { editingText = it },
                onCancelEdit = { isInlineEditing = false; editingIndex = -1 },
                onConfirmEdit = { idx, txt -> handleTtsClick(idx, txt); isInlineEditing = false; editingIndex = -1 },
                onItemClick = { idx, res -> 
                    if (userSettings.inlineEdit) {
                        isInlineEditing = true
                        editingIndex = idx
                        editingText = res.modifiedText
                        
                        if (res.wavFilePath.isNotEmpty()) {
                            coroutineScope.launch {
                                isFetchingCandidates = true
                                if (res.localCandidates.isNotEmpty()) {
                                    localCandidate = res.localCandidates.firstOrNull()
                                } else {
                                    val localJob = launch(Dispatchers.IO) {
                                        try {
                                            val audioData = readWavFileToFloatArray(res.wavFilePath)
                                            if (audioData != null) {
                                                val recognizer = SimulateStreamingAsr.recognizer
                                                val stream = recognizer.createStream()
                                                stream.acceptWaveform(audioData, 16000)
                                                recognizer.decode(stream)
                                                val localResultText = recognizer.getResult(stream).text
                                                stream.release()
                                                if (localResultText.isNotBlank()) {
                                                    val updatedItem = res.copy(localCandidates = listOf(localResultText))
                                                    withContext(Dispatchers.Main) {
                                                        localCandidate = localResultText
                                                        if (idx in resultList.indices && resultList[idx].wavFilePath == res.wavFilePath) {
                                                            resultList[idx] = updatedItem
                                                        }
                                                    }
                                                    logHomeJsonlUpdate("local_rerecognition", updatedItem)
                                                    persistTranscriptJsonl(
                                                        context,
                                                        userSettings.userId,
                                                        updatedItem
                                                    )
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Local re-recognition failed", e)
                                        }
                                    }
                                    localJob.join()
                                }
                                isFetchingCandidates = false
                            }
                        }
                    } else {
                        isInlineEditing = false
                        editingIndex = -1
                        isFetchingCandidates = false
                        localCandidate = null
                        transcriptToEditInDialog = idx to res
                    }
                },
                onTtsClick = { idx, txt -> handleTtsClick(idx, txt) },
                onEnglishTtsClick = { transcript ->
                    MediaController.stop()
                    speechController.stop()
                    englishSpeechController.speak(transcript.englishTranslation)
                },
                onPlayClick = { path ->
                    if (currentlyPlaying == path) {
                        MediaController.stop()
                    } else {
                        MediaController.play(
                            context = context,
                            filePath = path,
                            userSettings = userSettings,
                            onRoutingApplied = homeViewModel::reportAudioRoutingMessage
                        )
                    }
                },
                onDeleteClick = { idx, path ->
                    if (path.isNotEmpty() && deleteTranscriptFiles(path)) {
                        clearCorrectionState(path)
                        clearTranslationState(path)
                        resultList.removeAt(idx)
                    }
                },
                isRecognizingSpeech = isRecognizingSpeech,
                currentlyPlaying = currentlyPlaying,
                isStarted = isStarted,
                isTtsSpeaking = isAnyTtsSpeaking,
                countdownProgress = countdownProgress,
                isDataCollectMode = false,
                inlineEditEnabled = userSettings.inlineEdit,
                localCandidate = localCandidate,
                isFetchingCandidates = isFetchingCandidates,
                showEnglishTranslation = userSettings.enableHomeEnglishTranslation,
                isLlmCorrectionRunning = { transcript ->
                    transcript.wavFilePath.isNotBlank() && correctionRunning[transcript.wavFilePath] == true
                }
            )
        }

        transcriptToEditInDialog?.let { (index, transcript) ->
            EditRecognitionDialog(
                originalText = transcript.recognizedText,
                currentText = transcript.modifiedText,
                wavFilePath = transcript.wavFilePath,
                onDismiss = {
                    transcriptToEditInDialog = null
                },
                onConfirm = { newText ->
                    val updatedItem = reduceTranscriptAfterConfirmation(
                        transcript = transcript,
                        newText = newText,
                        lockTranscript = false
                    )
                    updateTranscriptAndRefreshEnglish(
                        index = index,
                        updatedTranscript = updatedItem,
                        previousModifiedText = transcript.modifiedText
                    )
                    coroutineScope.launch(Dispatchers.IO) {
                        val latestTranscript = resultList.getOrNull(index) ?: return@launch
                        logHomeJsonlUpdate("dialog_confirm", latestTranscript)
                        persistTranscriptJsonl(context, userSettings.userId, latestTranscript)
                    }
                    transcriptToEditInDialog = null
                },
                onSpeakConfirm = { newText ->
                    handleDialogTtsConfirm(index, newText)
                },
                userId = userSettings.userId,
                backendUrl = userSettings.backendUrl,
            )
        }
    }
}

@Composable
private fun HomeButtonRow(
    isStarted: Boolean,
    onRecordingButtonClick: () -> Unit,
    onCopyButtonClick: () -> Unit,
    onClearButtonClick: () -> Unit,
    isPlaybackActive: Boolean,
    isAsrModelLoading: Boolean,
    isEditing: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Button(onClick = onRecordingButtonClick, enabled = !isPlaybackActive && !isAsrModelLoading && !isEditing) {
            Text(text = stringResource(if (isStarted) R.string.stop else R.string.start))
        }
        Spacer(modifier = Modifier.width(24.dp))
        Button(onClick = onCopyButtonClick, enabled = !isStarted && !isPlaybackActive && !isEditing) {
            Text(text = stringResource(id = R.string.copy))
        }
        Spacer(modifier = Modifier.width(24.dp))
        Button(onClick = onClearButtonClick, enabled = !isStarted && !isPlaybackActive && !isEditing) {
            Text(text = stringResource(id = R.string.clear))
        }
    }
}
