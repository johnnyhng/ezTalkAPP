package tw.com.johnnyhng.eztalk.asr.speaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.llm.LlmResponse

class SpeakerSemanticModuleTest {
    private val module = SpeakerSemanticModule()
    private val lines = listOf(
        "alpha",
        "play the weather report",
        "closing line"
    )
    private val rankedResults = listOf(
        SpeakerSearchResult(
            documentId = "doc-1",
            lineStart = 1,
            lineEnd = 1,
            matchedText = "play the weather report",
            semanticScore = 0.8f,
            lexicalScore = 0.7f,
            finalScore = 0.78f
        )
    )

    @Test
    fun parseLlmResponse_acceptsStructuredJsonCandidate() {
        val response = LlmResponse(
            rawText = """{"decision":"candidate","lineStart":1,"lineEnd":1,"reason":"close lexical hit"}"""
        )

        val decision = module.parseLlmResponse(
            response = response,
            rankedResults = rankedResults,
            lines = lines
        )

        assertTrue(decision is SpeakerSemanticDecision.Candidate)
        val candidate = decision as SpeakerSemanticDecision.Candidate
        assertEquals(1, candidate.lineIndex)
        assertEquals(1, candidate.result.lineStart)
    }

    @Test
    fun parseLlmResponse_acceptsFencedJsonAutoplay() {
        val response = LlmResponse(
            rawText = """
                ```json
                {"decision":"autoplay","lineStart":1,"lineEnd":1}
                ```
            """.trimIndent()
        )

        val decision = module.parseLlmResponse(
            response = response,
            rankedResults = rankedResults,
            lines = lines
        )

        assertTrue(decision is SpeakerSemanticDecision.AutoPlay)
        val autoplay = decision as SpeakerSemanticDecision.AutoPlay
        assertEquals(1, autoplay.lineIndex)
    }

    @Test
    fun parseLlmResponse_acceptsPlainFencedJsonCandidate() {
        val response = LlmResponse(
            rawText = """
                ```
                {"decision":"candidate","lineStart":1,"lineEnd":1}
                ```
            """.trimIndent()
        )

        val decision = module.parseLlmResponse(
            response = response,
            rankedResults = rankedResults,
            lines = lines
        )

        assertTrue(decision is SpeakerSemanticDecision.Candidate)
    }

    @Test
    fun parseLlmResponse_rejectsCandidateWithoutLineRange() {
        val response = LlmResponse(
            rawText = """{"decision":"candidate","reason":"missing location"}"""
        )

        val decision = module.parseLlmResponse(
            response = response,
            rankedResults = rankedResults,
            lines = lines
        )

        assertEquals(SpeakerSemanticDecision.NoMatch, decision)
    }
}
