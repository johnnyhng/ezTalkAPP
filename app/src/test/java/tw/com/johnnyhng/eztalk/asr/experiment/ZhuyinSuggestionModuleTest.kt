package tw.com.johnnyhng.eztalk.asr.experiment

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.llm.LlmOutputFormat
import tw.com.johnnyhng.eztalk.asr.llm.LlmProvider
import tw.com.johnnyhng.eztalk.asr.llm.LlmRequest
import tw.com.johnnyhng.eztalk.asr.llm.LlmResponse

class ZhuyinSuggestionModuleTest {
    @Test
    fun suggestWordsBuildsJsonRequestAndParsesCandidates() = runBlocking {
        val provider = FakeLlmProvider(
            Result.success(LlmResponse("""{"candidates":["想","休息"]}"""))
        )
        val module = ZhuyinSuggestionModule(
            llmProvider = provider,
            llmModel = "gemini-2.5-flash"
        )

        val result = module.suggestWords(ZhuyinPromptContext(text = "我想ㄒ"))

        assertEquals(listOf("想", "休息"), result.getOrThrow())
        assertEquals("gemini-2.5-flash", provider.requests.single().model)
        assertEquals(LlmOutputFormat.JSON, provider.requests.single().outputFormat)
        assertTrue(provider.requests.single().userPrompt.contains("目前輸入：我想ㄒ"))
        assertTrue(provider.requests.single().userPrompt.contains("候選字詞"))
    }

    @Test
    fun suggestSentencesBuildsSentencePrompt() = runBlocking {
        val provider = FakeLlmProvider(
            Result.success(LlmResponse("""{"candidates":["我要喝水。"]}"""))
        )
        val module = ZhuyinSuggestionModule(llmProvider = provider)

        val result = module.suggestSentences(ZhuyinPromptContext(text = "ㄨㄛˇy"))

        assertEquals(listOf("我要喝水。"), result.getOrThrow())
        assertTrue(provider.requests.single().userPrompt.contains("完整句子"))
    }

    @Test
    fun suggestWordsReturnsEmptyListWhenProviderMissing() = runBlocking {
        val module = ZhuyinSuggestionModule(llmProvider = null)

        val result = module.suggestWords(ZhuyinPromptContext(text = "ㄋ"))

        assertEquals(emptyList<String>(), result.getOrThrow())
    }

    @Test
    fun suggestWordsReturnsEmptyListWhenModelDisabled() = runBlocking {
        val provider = FakeLlmProvider(
            Result.success(LlmResponse("""{"candidates":["你"]}"""))
        )
        val module = ZhuyinSuggestionModule(llmProvider = provider, llmModel = "none")

        val result = module.suggestWords(ZhuyinPromptContext(text = "ㄋ"))

        assertEquals(emptyList<String>(), result.getOrThrow())
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun suggestWordsPropagatesProviderFailure() = runBlocking {
        val module = ZhuyinSuggestionModule(
            llmProvider = FakeLlmProvider(Result.failure(IllegalStateException("failed")))
        )

        val result = module.suggestWords(ZhuyinPromptContext(text = "ㄋ"))

        assertTrue(result.isFailure)
    }

    private class FakeLlmProvider(
        private val result: Result<LlmResponse>
    ) : LlmProvider {
        override val providerName: String = "fake"
        val requests = mutableListOf<LlmRequest>()

        override suspend fun generate(request: LlmRequest): Result<LlmResponse> {
            requests += request
            return result
        }
    }
}
