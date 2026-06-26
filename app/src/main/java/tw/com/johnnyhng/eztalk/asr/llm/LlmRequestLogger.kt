package tw.com.johnnyhng.eztalk.asr.llm

internal object LlmRequestLogger {
    private const val LOG_CHUNK_SIZE = 3000

    fun log(
        source: String,
        provider: LlmProvider,
        request: LlmRequest
    ) {
        safeInfo(
            "LLM request source=$source provider=${provider.providerName} " +
                "model=${request.model.ifBlank { "(default/provider)" }} " +
                "outputFormat=${request.outputFormat} systemLength=${request.systemInstruction?.length ?: 0} " +
                "userLength=${request.userPrompt.length} schemaLength=${request.schemaHint?.length ?: 0} " +
                "maxOutputTokens=${request.maxOutputTokens ?: "default"} temperature=${request.temperature ?: "default"} " +
                "stopSequences=${request.stopSequences.size}"
        )

        logField(source, "system", request.systemInstruction.orEmpty())
        logField(source, "user", request.userPrompt)
        logField(source, "schema", request.schemaHint.orEmpty())
        if (request.stopSequences.isNotEmpty()) {
            logField(source, "stopSequences", request.stopSequences.joinToString(separator = "\n"))
        }
    }

    fun logCompletion(
        source: String,
        provider: LlmProvider,
        request: LlmRequest,
        durationMs: Long,
        result: Result<LlmResponse>
    ) {
        result.fold(
            onSuccess = { response ->
                safeInfo(
                    "LLM response source=$source provider=${provider.providerName} " +
                        "model=${request.model.ifBlank { "(default/provider)" }} " +
                        "durationMs=$durationMs success=true rawLength=${response.rawText.length} " +
                        "finishReason=${response.finishReason ?: "none"} " +
                        "inputTokens=${response.usage?.inputTokens ?: "unknown"} " +
                        "outputTokens=${response.usage?.outputTokens ?: "unknown"} " +
                        "totalTokens=${response.usage?.totalTokens ?: "unknown"}"
                )
            },
            onFailure = { error ->
                safeInfo(
                    "LLM response source=$source provider=${provider.providerName} " +
                        "model=${request.model.ifBlank { "(default/provider)" }} " +
                        "durationMs=$durationMs success=false errorType=${error.javaClass.simpleName} " +
                        "error=${error.message ?: "unknown"}"
                )
            }
        )
    }

    private fun logField(source: String, field: String, value: String) {
        val text = value.trim()
        if (text.isBlank()) {
            safeDebug("LLM request prompt source=$source field=$field chunk=0/0: (blank)")
            return
        }

        val chunks = text.chunked(LOG_CHUNK_SIZE)
        chunks.forEachIndexed { index, chunk ->
            safeDebug(
                "LLM request prompt source=$source field=$field chunk=${index + 1}/${chunks.size}:\n$chunk"
            )
        }
    }

    private fun safeInfo(message: String) {
        safeLogInfo(LLM_LOG_TAG, message)
    }

    private fun safeDebug(message: String) {
        safeLogDebug(LLM_LOG_TAG, message)
    }
}

internal suspend fun LlmProvider.generateLogged(
    request: LlmRequest,
    source: String
): Result<LlmResponse> {
    LlmRequestLogger.log(
        source = source,
        provider = this,
        request = request
    )
    val startTime = System.currentTimeMillis()
    val result = generate(request)
    LlmRequestLogger.logCompletion(
        source = source,
        provider = this,
        request = request,
        durationMs = System.currentTimeMillis() - startTime,
        result = result
    )
    return result
}
