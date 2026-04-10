package tw.com.johnnyhng.eztalk.asr.prompt

internal data class PromptTemplate(
    val systemInstruction: String,
    val userPrompt: String,
    val expectedResponseSchema: String? = null
)
