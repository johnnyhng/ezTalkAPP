package tw.com.johnnyhng.eztalk.asr.llm

import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG

internal object LlmRequestLogger {
    private const val LOG_CHUNK_SIZE = 3000

    fun log(
        source: String,
        provider: LlmProvider,
        request: LlmRequest
    ) {
        safeInfo(
            TAG,
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

    private fun logField(source: String, field: String, value: String) {
        val text = value.trim()
        if (text.isBlank()) {
            safeDebug(TAG, "LLM request prompt source=$source field=$field chunk=0/0: (blank)")
            return
        }

        val chunks = text.chunked(LOG_CHUNK_SIZE)
        chunks.forEachIndexed { index, chunk ->
            safeDebug(
                TAG,
                "LLM request prompt source=$source field=$field chunk=${index + 1}/${chunks.size}:\n$chunk"
            )
        }
    }

    private fun safeInfo(tag: String, message: String) {
        try {
            Log.i(tag, message)
        } catch (_: RuntimeException) {
            // android.util.Log is not mocked in local JVM unit tests.
        }
    }

    private fun safeDebug(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (_: RuntimeException) {
            // android.util.Log is not mocked in local JVM unit tests.
        }
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
    return generate(request)
}
