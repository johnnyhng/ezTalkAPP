package tw.com.johnnyhng.eztalk.asr.experiment

internal enum class ExperimentSuggestionMode {
    WORD,
    SENTENCE
}

internal data class ExperimentUiState(
    val inputText: String = "",
    val selectedEmotion: ZhuyinEmotion = traditionalChineseEmotions.first(),
    val scenarios: List<ExperimentScenario> = defaultScenarios,
    val selectedScenario: ExperimentScenario = defaultScenarios.first(),
    val suggestionMode: ExperimentSuggestionMode = ExperimentSuggestionMode.WORD,
    val wordCandidates: List<String> = emptyList(),
    val sentenceCandidates: List<String> = emptyList(),
    val conversationHistory: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isThinking: Boolean = false,
    val isSentenceThinking: Boolean = false,
    val hasRequestedSuggestions: Boolean = false,
    val lastRequestDurationMs: Long = 0L,
    val errorMessage: String? = null
)

internal fun reduceExperimentCharacterInput(
    state: ExperimentUiState,
    value: String
): ExperimentUiState {
    val textValue = if (value == "␣") " " else value
    return state.copy(
        inputText = state.inputText + textValue,
        wordCandidates = emptyList(),
        sentenceCandidates = emptyList(),
        hasRequestedSuggestions = false,
        errorMessage = null
    )
}

internal fun reduceExperimentInitialPhraseInput(
    state: ExperimentUiState,
    phrase: String
): ExperimentUiState {
    return state.copy(
        inputText = state.inputText + phrase,
        wordCandidates = emptyList(),
        sentenceCandidates = emptyList(),
        hasRequestedSuggestions = false,
        errorMessage = null
    )
}

internal fun reduceExperimentBackspace(state: ExperimentUiState): ExperimentUiState {
    return state.copy(
        inputText = state.inputText.dropLast(1),
        wordCandidates = emptyList(),
        sentenceCandidates = emptyList(),
        hasRequestedSuggestions = false,
        errorMessage = null
    )
}

internal fun reduceExperimentClear(state: ExperimentUiState): ExperimentUiState {
    val nextHistory = if (state.inputText.isBlank()) {
        state.conversationHistory
    } else {
        (state.conversationHistory + state.inputText).takeLast(20)
    }
    return state.copy(
        inputText = "",
        wordCandidates = emptyList(),
        sentenceCandidates = emptyList(),
        conversationHistory = nextHistory,
        hasRequestedSuggestions = false,
        errorMessage = null
    )
}

internal fun reduceExperimentEmotionSelection(
    state: ExperimentUiState,
    emotion: ZhuyinEmotion
): ExperimentUiState {
    return state.copy(selectedEmotion = emotion, errorMessage = null)
}

internal fun reduceExperimentSuggestionLoading(
    state: ExperimentUiState,
    mode: ExperimentSuggestionMode
): ExperimentUiState {
    return state.copy(
        suggestionMode = mode,
        isLoading = true,
        isThinking = mode == ExperimentSuggestionMode.WORD,
        isSentenceThinking = mode == ExperimentSuggestionMode.SENTENCE,
        hasRequestedSuggestions = true,
        wordCandidates = if (mode == ExperimentSuggestionMode.WORD) emptyList() else state.wordCandidates,
        sentenceCandidates = if (mode == ExperimentSuggestionMode.SENTENCE) emptyList() else state.sentenceCandidates,
        errorMessage = null
    )
}

internal fun reduceExperimentSuggestionSuccess(
    state: ExperimentUiState,
    candidates: List<String>,
    mode: ExperimentSuggestionMode
): ExperimentUiState {
    return state.copy(
        isLoading = state.isThinking && state.isSentenceThinking, // only false if BOTH finished
        isThinking = if (mode == ExperimentSuggestionMode.WORD) false else state.isThinking,
        isSentenceThinking = if (mode == ExperimentSuggestionMode.SENTENCE) false else state.isSentenceThinking,
        hasRequestedSuggestions = true,
        wordCandidates = if (mode == ExperimentSuggestionMode.WORD) candidates else state.wordCandidates,
        sentenceCandidates = if (mode == ExperimentSuggestionMode.SENTENCE) candidates else state.sentenceCandidates,
        errorMessage = null
    )
}

internal fun reduceExperimentSuggestionFailure(
    state: ExperimentUiState,
    message: String,
    mode: ExperimentSuggestionMode
): ExperimentUiState {
    return state.copy(
        isLoading = state.isThinking && state.isSentenceThinking,
        isThinking = if (mode == ExperimentSuggestionMode.WORD) false else state.isThinking,
        isSentenceThinking = if (mode == ExperimentSuggestionMode.SENTENCE) false else state.isSentenceThinking,
        wordCandidates = if (mode == ExperimentSuggestionMode.WORD) emptyList() else state.wordCandidates,
        sentenceCandidates = if (mode == ExperimentSuggestionMode.SENTENCE) emptyList() else state.sentenceCandidates,
        hasRequestedSuggestions = true,
        errorMessage = message
    )
}

internal fun reduceExperimentCandidateApply(
    state: ExperimentUiState,
    candidate: String
): ExperimentUiState {
    // Both words and sentences are now treated as suffixes (completions).
    // We strip trailing Zhuyin and append the candidate.
    val nextText = appendZhuyinCandidate(state.inputText, candidate)
    
    return state.copy(
        inputText = nextText,
        wordCandidates = emptyList(),
        sentenceCandidates = emptyList(),
        hasRequestedSuggestions = false,
        errorMessage = null
    )
}

internal fun ExperimentUiState.toZhuyinPromptContext(
    candidateCount: Int = 6
): ZhuyinPromptContext {
    return ZhuyinPromptContext(
        text = inputText,
        candidateCount = candidateCount,
        selectedEmotionPrompt = selectedEmotion.prompt,
        scenarioKeywords = selectedScenario.keywords,
        scenarioInstruction = selectedScenario.customInstruction,
        conversationHistory = conversationHistory
    )
}
