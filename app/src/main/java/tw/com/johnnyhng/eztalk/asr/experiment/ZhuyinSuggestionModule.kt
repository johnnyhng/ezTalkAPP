package tw.com.johnnyhng.eztalk.asr.experiment

import tw.com.johnnyhng.eztalk.asr.llm.LlmOutputFormat
import tw.com.johnnyhng.eztalk.asr.llm.LlmProvider
import tw.com.johnnyhng.eztalk.asr.llm.LlmRequest

internal interface ZhuyinSuggestionProvider {
    suspend fun suggestWords(context: ZhuyinPromptContext): Result<List<String>>

    suspend fun suggestSentences(context: ZhuyinPromptContext): Result<List<String>>
}

internal class ZhuyinSuggestionModule(
    private val llmProvider: LlmProvider? = null,
    private val llmModel: String = "gemini-2.5-flash",
    private val wordPromptBuilder: ZhuyinWordPromptBuilder = ZhuyinWordPromptBuilder(),
    private val sentencePromptBuilder: ZhuyinSentencePromptBuilder = ZhuyinSentencePromptBuilder()
) : ZhuyinSuggestionProvider {
    override suspend fun suggestWords(context: ZhuyinPromptContext): Result<List<String>> {
        return generateCandidates(wordPromptBuilder.build(context), context)
    }

    override suspend fun suggestSentences(context: ZhuyinPromptContext): Result<List<String>> {
        return generateCandidates(sentencePromptBuilder.build(context), context)
    }

    private suspend fun generateCandidates(
        prompt: tw.com.johnnyhng.eztalk.asr.prompt.PromptTemplate,
        context: ZhuyinPromptContext
    ): Result<List<String>> {
        val provider = llmProvider ?: return Result.success(emptyList())
        if (llmModel.isBlank() || llmModel == "none") return Result.success(emptyList())

        val request = LlmRequest(
            model = llmModel,
            systemInstruction = prompt.systemInstruction,
            userPrompt = prompt.userPrompt,
            outputFormat = LlmOutputFormat.TEXT, // 切換為純文字
            schemaHint = prompt.expectedResponseSchema,
            stopSequences = context.stopSequences,
            maxOutputTokens = context.maxOutputTokens,
            temperature = context.temperature
        )

        return provider.generate(request).map { response ->
            parseZhuyinCandidates(response.rawText)
        }
    }
}
