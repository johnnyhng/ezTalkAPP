package tw.com.johnnyhng.eztalk.asr.experiment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
internal class ExperimentViewModel(
    private val suggestionProvider: ZhuyinSuggestionProvider = ZhuyinSuggestionModule()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExperimentUiState())
    val uiState: StateFlow<ExperimentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState
                .map { it.inputText }
                .distinctUntilChanged()
                .debounce(600)
                .collect { text ->
                    if (text.isNotBlank()) {
                        requestSuggestions(ExperimentSuggestionMode.WORD)
                        requestSuggestions(ExperimentSuggestionMode.SENTENCE)
                    }
                }
        }
    }

    fun inputCharacter(value: String) {
        _uiState.update { reduceExperimentCharacterInput(it, value) }
    }

    fun inputInitialPhrase(phrase: String) {
        _uiState.update { reduceExperimentInitialPhraseInput(it, phrase) }
    }

    fun selectEmotion(emotion: ZhuyinEmotion) {
        _uiState.update { reduceExperimentEmotionSelection(it, emotion) }
    }

    fun backspace() {
        _uiState.update(::reduceExperimentBackspace)
    }

    fun clear() {
        _uiState.update(::reduceExperimentClear)
    }

    fun applyCandidate(candidate: String) {
        _uiState.update { reduceExperimentCandidateApply(it, candidate) }
    }

    fun requestWordSuggestions() {
        requestSuggestions(ExperimentSuggestionMode.WORD)
    }

    fun requestSentenceSuggestions() {
        requestSuggestions(ExperimentSuggestionMode.SENTENCE)
    }

    private fun requestSuggestions(mode: ExperimentSuggestionMode) {
        _uiState.update { reduceExperimentSuggestionLoading(it, mode) }
        viewModelScope.launch {
            val context = _uiState.value.toZhuyinPromptContext()
            val result = when (mode) {
                ExperimentSuggestionMode.WORD -> suggestionProvider.suggestWords(context)
                ExperimentSuggestionMode.SENTENCE -> suggestionProvider.suggestSentences(context)
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { reduceExperimentSuggestionSuccess(state, it) },
                    onFailure = { error ->
                        reduceExperimentSuggestionFailure(
                            state,
                            error.message ?: error.javaClass.simpleName
                        )
                    }
                )
            }
        }
    }
}

internal class ExperimentViewModelFactory(
    private val suggestionProvider: ZhuyinSuggestionProvider
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExperimentViewModel::class.java)) {
            return ExperimentViewModel(suggestionProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
