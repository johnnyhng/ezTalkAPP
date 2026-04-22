package tw.com.johnnyhng.eztalk.asr.experiment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlinx.coroutines.withTimeoutOrNull

@OptIn(FlowPreview::class)
internal class ExperimentViewModel(
    private val suggestionProvider: ZhuyinSuggestionProvider = ZhuyinSuggestionModule(),
    private val contextRepository: ExperimentContextRepository = FirebaseExperimentContextRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExperimentUiState())
    val uiState: StateFlow<ExperimentUiState> = _uiState.asStateFlow()

    init {
        fetchScenarios()
        viewModelScope.launch {
            _uiState
                .map { it.inputText }
                .distinctUntilChanged()
                .debounce(600)
                .collect { text ->
                    if (text.isNotBlank()) {
                        requestAllSuggestions(text)
                    }
                }
        }
    }

    private fun fetchScenarios() {
        viewModelScope.launch {
            val scenarios = contextRepository.listScenarios()
            _uiState.update { it.copy(
                scenarios = scenarios,
                selectedScenario = scenarios.firstOrNull { s -> s.id == it.selectedScenario.id } ?: scenarios.first()
            ) }
        }
    }

    fun selectScenario(scenario: ExperimentScenario) {
        _uiState.update { it.copy(selectedScenario = scenario) }
        requestAllSuggestions(_uiState.value.inputText)
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
        requestAllSuggestions(_uiState.value.inputText)
    }

    fun requestSentenceSuggestions() {
        requestAllSuggestions(_uiState.value.inputText)
    }

    private fun requestAllSuggestions(text: String) {
        if (text.isBlank()) return

        _uiState.update { state ->
            state.copy(
                isLoading = true,
                isThinking = true,
                isSentenceThinking = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val context = _uiState.value.copy(inputText = text).toZhuyinPromptContext()
            
            val wordResult = withTimeoutOrNull(10000L) {
                suggestionProvider.suggestWords(context)
            } ?: Result.failure(Exception("Word suggestion request timed out after 10s"))

            val sentenceResult = withTimeoutOrNull(10000L) {
                suggestionProvider.suggestSentences(context)
            } ?: Result.failure(Exception("Sentence suggestion request timed out after 10s"))

            _uiState.update { state ->
                // Guard against stale text
                if (state.inputText != text) return@update state

                state.copy(
                    isLoading = false,
                    isThinking = false,
                    isSentenceThinking = false,
                    hasRequestedSuggestions = true,
                    wordCandidates = wordResult.getOrDefault(emptyList()),
                    sentenceCandidates = sentenceResult.getOrDefault(emptyList()),
                    errorMessage = if (wordResult.isFailure) wordResult.exceptionOrNull()?.message
                        else if (sentenceResult.isFailure) sentenceResult.exceptionOrNull()?.message
                        else null
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
