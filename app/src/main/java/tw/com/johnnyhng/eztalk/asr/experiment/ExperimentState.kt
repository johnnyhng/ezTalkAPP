package tw.com.johnnyhng.eztalk.asr.experiment

internal enum class ExperimentSuggestionMode {
    WORD,
    SENTENCE
}

internal data class ExperimentUiState(
    val inputText: String = "",
    val selectedEmotion: ZhuyinEmotion = traditionalChineseEmotions.first(),
    val suggestionMode: ExperimentSuggestionMode = ExperimentSuggestionMode.WORD,
    val candidates: List<String> = emptyList(),
    val conversationHistory: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val hasRequestedSuggestions: Boolean = false,
    val errorMessage: String? = null
)

internal fun reduceExperimentCharacterInput(
    state: ExperimentUiState,
    value: String
): ExperimentUiState {
    val textValue = if (value == "␣") " " else value
    return state.copy(
        inputText = state.inputText + textValue,
        candidates = emptyList(),
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
        candidates = emptyList(),
        hasRequestedSuggestions = false,
        errorMessage = null
    )
}

internal fun reduceExperimentBackspace(state: ExperimentUiState): ExperimentUiState {
    return state.copy(
        inputText = state.inputText.dropLast(1),
        candidates = emptyList(),
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
        candidates = emptyList(),
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
        hasRequestedSuggestions = true,
        candidates = emptyList(),
        errorMessage = null
    )
}

internal fun reduceExperimentSuggestionSuccess(
    state: ExperimentUiState,
    candidates: List<String>
): ExperimentUiState {
    return state.copy(
        isLoading = false,
        hasRequestedSuggestions = true,
        candidates = candidates,
        errorMessage = null
    )
}

internal fun reduceExperimentSuggestionFailure(
    state: ExperimentUiState,
    message: String
): ExperimentUiState {
    return state.copy(
        isLoading = false,
        candidates = emptyList(),
        hasRequestedSuggestions = true,
        errorMessage = message
    )
}

internal fun reduceExperimentCandidateApply(
    state: ExperimentUiState,
    candidate: String
): ExperimentUiState {
    val nextText = when (state.suggestionMode) {
        ExperimentSuggestionMode.WORD -> appendZhuyinCandidate(state.inputText, candidate)
        ExperimentSuggestionMode.SENTENCE -> candidate
    }
    return state.copy(
        inputText = nextText,
        candidates = emptyList(),
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
        conversationHistory = conversationHistory
    )
}
