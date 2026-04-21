package tw.com.johnnyhng.eztalk.asr.experiment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentStateTest {
    @Test
    fun characterInputAppendsAndMapsVisibleSpaceToken() {
        val state = reduceExperimentCharacterInput(
            reduceExperimentCharacterInput(ExperimentUiState(), "ㄋ"),
            "␣"
        )

        assertEquals("ㄋ ", state.inputText)
        assertTrue(state.wordCandidates.isEmpty())
        assertTrue(state.sentenceCandidates.isEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun initialPhraseInputAppendsPhrase() {
        val state = reduceExperimentInitialPhraseInput(ExperimentUiState(inputText = "你好"), "謝謝")

        assertEquals("你好謝謝", state.inputText)
    }

    @Test
    fun backspaceDropsLastCharacterAndClearsCandidates() {
        val state = reduceExperimentBackspace(
            ExperimentUiState(
                inputText = "我想ㄒ",
                wordCandidates = listOf("休息"),
                sentenceCandidates = listOf("我想休息。")
            )
        )

        assertEquals("我想", state.inputText)
        assertTrue(state.wordCandidates.isEmpty())
        assertTrue(state.sentenceCandidates.isEmpty())
    }

    @Test
    fun clearStoresNonBlankInputInHistory() {
        val state = reduceExperimentClear(
            ExperimentUiState(inputText = "我要喝水", conversationHistory = listOf("你好"))
        )

        assertEquals("", state.inputText)
        assertEquals(listOf("你好", "我要喝水"), state.conversationHistory)
    }

    @Test
    fun clearDoesNotStoreBlankInput() {
        val state = reduceExperimentClear(
            ExperimentUiState(inputText = " ", conversationHistory = listOf("你好"))
        )

        assertEquals(listOf("你好"), state.conversationHistory)
    }

    @Test
    fun emotionSelectionUpdatesSelectedEmotion() {
        val emotion = traditionalChineseEmotions.first { it.label == "提問" }
        val state = reduceExperimentEmotionSelection(ExperimentUiState(), emotion)

        assertEquals("疑問", state.selectedEmotion.prompt)
    }

    @Test
    fun suggestionLoadingSetsModeAndLoadingState() {
        val state = reduceExperimentSuggestionLoading(
            ExperimentUiState(wordCandidates = listOf("你"), errorMessage = "old"),
            ExperimentSuggestionMode.SENTENCE
        )

        assertEquals(ExperimentSuggestionMode.SENTENCE, state.suggestionMode)
        assertTrue(state.isLoading)
        assertTrue(state.isSentenceThinking)
        assertFalse(state.isThinking)
        assertTrue(state.hasRequestedSuggestions)
        assertTrue(state.sentenceCandidates.isEmpty())
        assertEquals(listOf("你"), state.wordCandidates) // WORD candidates preserved
        assertNull(state.errorMessage)
    }

    @Test
    fun suggestionSuccessStoresCandidatesAndStopsLoading() {
        val state = reduceExperimentSuggestionSuccess(
            ExperimentUiState(isLoading = true, suggestionMode = ExperimentSuggestionMode.WORD),
            listOf("你", "您")
        )

        assertFalse(state.isLoading)
        assertFalse(state.isThinking)
        assertTrue(state.hasRequestedSuggestions)
        assertEquals(listOf("你", "您"), state.wordCandidates)
    }

    @Test
    fun suggestionFailureStoresMessageAndClearsModeCandidates() {
        val state = reduceExperimentSuggestionFailure(
            ExperimentUiState(
                isLoading = true,
                suggestionMode = ExperimentSuggestionMode.WORD,
                wordCandidates = listOf("你")
            ),
            "Gemini failed"
        )

        assertFalse(state.isLoading)
        assertFalse(state.isThinking)
        assertTrue(state.hasRequestedSuggestions)
        assertTrue(state.wordCandidates.isEmpty())
        assertEquals("Gemini failed", state.errorMessage)
    }

    @Test
    fun applyWordCandidateUsesZhuyinAppendSemantics() {
        val state = reduceExperimentCandidateApply(
            ExperimentUiState(
                inputText = "我想ㄒ",
                suggestionMode = ExperimentSuggestionMode.WORD,
                wordCandidates = listOf("休息")
            ),
            "休息"
        )

        assertEquals("我想休息", state.inputText)
        assertTrue(state.wordCandidates.isEmpty())
        assertFalse(state.hasRequestedSuggestions)
    }

    @Test
    fun applySentenceCandidateReplacesInput() {
        val state = reduceExperimentCandidateApply(
            ExperimentUiState(
                inputText = "ㄨㄛˇy",
                suggestionMode = ExperimentSuggestionMode.SENTENCE,
                sentenceCandidates = listOf("我要喝水。")
            ),
            "我要喝水。"
        )

        assertEquals("我要喝水。", state.inputText)
        assertTrue(state.sentenceCandidates.isEmpty())
    }

    @Test
    fun promptContextUsesCurrentState() {
        val state = ExperimentUiState(
            inputText = "我想ㄒ",
            selectedEmotion = traditionalChineseEmotions.first { it.label == "拜託" },
            conversationHistory = listOf("你好")
        )

        val context = state.toZhuyinPromptContext(candidateCount = 4)

        assertEquals("我想ㄒ", context.text)
        assertEquals(4, context.candidateCount)
        assertEquals("請求", context.selectedEmotionPrompt)
        assertEquals(listOf("你好"), context.conversationHistory)
    }
}
