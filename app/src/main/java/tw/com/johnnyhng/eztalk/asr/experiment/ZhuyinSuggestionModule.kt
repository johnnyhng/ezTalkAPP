package tw.com.johnnyhng.eztalk.asr.experiment

import tw.com.johnnyhng.eztalk.asr.llm.LlmOutputFormat
import tw.com.johnnyhng.eztalk.asr.llm.LlmProvider
import tw.com.johnnyhng.eztalk.asr.llm.LlmRequest

internal class ZhuyinSuggestionModule(
    private val llmProvider: LlmProvider? = null,
    private val llmModel: String = "gemini-2.5-flash",
    private val wordPromptBuilder: ZhuyinWordPromptBuilder = ZhuyinWordPromptBuilder(),
    private val sentencePromptBuilder: ZhuyinSentencePromptBuilder = ZhuyinSentencePromptBuilder()
) {
    suspend fun suggestWords(context: ZhuyinPromptContext): Result<List<String>> {
        return generateCandidates(wordPromptBuilder.build(context))
    }

    suspend fun suggestSentences(context: ZhuyinPromptContext): Result<List<String>> {
        return generateCandidates(sentencePromptBuilder.build(context))
    }

    private suspend fun generateCandidates(
        prompt: tw.com.johnnyhng.eztalk.asr.prompt.PromptTemplate
    ): Result<List<String>> {
        val provider = llmProvider ?: return Result.success(emptyList())
        if (llmModel.isBlank() || llmModel == "none") return Result.success(emptyList())

        val request = LlmRequest(
            model = llmModel,
            systemInstruction = prompt.systemInstruction,
            userPrompt = prompt.userPrompt,
            outputFormat = LlmOutputFormat.JSON,
            schemaHint = prompt.expectedResponseSchema
        )

        return provider.generate(request).map { response ->
            parseZhuyinCandidates(response.rawText)
        }
    }
}
