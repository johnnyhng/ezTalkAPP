package tw.com.johnnyhng.eztalk.asr.prompt

import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptCorrectionPromptBuilderTest {
    @Test
    fun buildStatesBackendCandidatesAreOnlyAdditionalVariants() {
        val prompt = TranscriptCorrectionPromptBuilder().build(
            utteranceVariants = listOf("我的名子叫做火瀚", "我的名字叫做黃瀚勳"),
            contextLines = listOf("你知不知道問題在哪裡")
        )

        assertTrue(prompt.systemInstruction.contains("backend recognition only as additional variants"))
        assertTrue(prompt.systemInstruction.contains("do not trust it automatically"))
        assertTrue(prompt.systemInstruction.contains("not limited to selecting an existing variant"))
    }

    @Test
    fun buildStatesLengthRepetitionAndConfidenceReplacementRules() {
        val prompt = TranscriptCorrectionPromptBuilder().build(
            utteranceVariants = listOf("很高興可以認識", "很高興可以認識你"),
            contextLines = emptyList()
        )

        assertTrue(prompt.systemInstruction.contains("Prefer longer variants"))
        assertTrue(prompt.systemInstruction.contains("repeated characters, repeated words, or phonetic similarity"))
        assertTrue(prompt.systemInstruction.contains("reliable enough to auto-replace"))
        assertTrue(prompt.systemInstruction.contains("confidence is below 0.85"))
    }

    @Test
    fun buildAllowsSemanticRestorationWhenVariantsAreIncomplete() {
        val prompt = TranscriptCorrectionPromptBuilder().build(
            utteranceVariants = listOf("我想", "我想要", "我想要喝", "想要喝水"),
            contextLines = listOf("你要喝什麼")
        )

        assertTrue(prompt.systemInstruction.contains("If all variants are incomplete"))
        assertTrue(prompt.systemInstruction.contains("restore a complete sentence"))
        assertTrue(prompt.systemInstruction.contains("may contain words that are not exactly present in any single variant"))
        assertTrue(prompt.systemInstruction.contains("Do not hallucinate unrelated facts"))
    }
}
