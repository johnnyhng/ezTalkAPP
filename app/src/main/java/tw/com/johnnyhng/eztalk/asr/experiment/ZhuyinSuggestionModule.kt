package tw.com.johnnyhng.eztalk.asr.experiment

import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.llm.LlmOutputFormat
import tw.com.johnnyhng.eztalk.asr.llm.LlmProvider
import tw.com.johnnyhng.eztalk.asr.llm.LlmRequest
import tw.com.johnnyhng.eztalk.asr.llm.generateLogged

internal interface ZhuyinSuggestionProvider {
    suspend fun suggestWords(context: ZhuyinPromptContext): Result<List<String>>
    suspend fun suggestSentences(context: ZhuyinPromptContext): Result<List<String>>
    suspend fun suggestRefinement(context: ZhuyinPromptContext): Result<String>
}

internal class ZhuyinSuggestionModule(
    private val llmProvider: LlmProvider? = null,
    private val llmModel: String = "gemini-2.5-flash",
    private val wordPromptBuilder: ZhuyinWordPromptBuilder = ZhuyinWordPromptBuilder(),
    private val sentencePromptBuilder: ZhuyinSentencePromptBuilder = ZhuyinSentencePromptBuilder(),
    private val refinePromptBuilder: ZhuyinRefinePromptBuilder = ZhuyinRefinePromptBuilder()
) : ZhuyinSuggestionProvider {
    override suspend fun suggestWords(context: ZhuyinPromptContext): Result<List<String>> {
        return generateCandidates(wordPromptBuilder.build(context), context)
    }

    override suspend fun suggestSentences(context: ZhuyinPromptContext): Result<List<String>> {
        return generateCandidates(sentencePromptBuilder.build(context), context)
    }

    override suspend fun suggestRefinement(context: ZhuyinPromptContext): Result<String> {
        val prompt = refinePromptBuilder.build(context)
        val provider = llmProvider ?: return Result.success("")
        if (llmModel.isBlank() || llmModel == "none") return Result.success("")

        val request = LlmRequest(
            model = llmModel,
            systemInstruction = prompt.systemInstruction,
            userPrompt = prompt.userPrompt,
            outputFormat = LlmOutputFormat.TEXT,
            stopSequences = context.stopSequences,
            maxOutputTokens = context.maxOutputTokens,
            temperature = context.temperature
        )

        return provider.generateLogged(request, source = "zhuyin_refinement").map { it.rawText.trim() }
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

        return provider.generateLogged(request, source = "zhuyin_candidates").map { response ->
            val candidates = parseZhuyinCandidates(response.rawText)
            logZhuyinCandidates(candidates.size, response.rawText)
            candidates
        }
    }

    private fun logZhuyinCandidates(count: Int, rawText: String) {
        try {
            Log.i(
                TAG,
                "Zhuyin candidates parsed count=$count rawPreview=${rawText.take(200)}"
            )
        } catch (_: RuntimeException) {
            // android.util.Log is not mocked in local JVM unit tests.
        }
    }
}
