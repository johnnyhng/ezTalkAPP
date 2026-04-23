package tw.com.johnnyhng.eztalk.asr.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.utils.TLSExpireResolver

internal data class GeminiProviderConfig(
    val model: String = "gemini-2.5-flash",
    val endpoint: String = "https://generativelanguage.googleapis.com"
)

internal class GeminiLlmProvider(
    private val config: GeminiProviderConfig = GeminiProviderConfig(),
    private val accessTokenProvider: GeminiAccessTokenProvider? = null,
    private val apiClient: GeminiApiClient = DefaultGeminiApiClient(config.endpoint)
) : LlmProvider {
    override val providerName: String = "gemini"

    override suspend fun generate(
        request: LlmRequest
    ): Result<LlmResponse> {
        val startTime = System.currentTimeMillis()
        Log.i(
            TAG,
            "Gemini generate start model=${request.model.ifBlank { config.model }} " +
                "promptLength=${request.userPrompt.length} outputFormat=${request.outputFormat}"
        )
        val tokenProvider = accessTokenProvider ?: return Result.failure(
            LlmError.ProviderFailure(
                detail = "Gemini OAuth token provider is not configured"
            )
        )

        val token = tokenProvider.fetchToken().getOrElse { error ->
            return Result.failure(
                LlmError.ProviderFailure(
                    detail = "Unable to acquire Gemini OAuth access token",
                    original = error
                )
            )
        }

        val model = request.model.ifBlank { config.model }
        Log.i(TAG, "Gemini generate acquired OAuth token for model=$model")
        val result = executeGenerateContent(
            model = model,
            token = token,
            request = request,
            allowRetryAfterUnauthorized = true
        )
        val duration = System.currentTimeMillis() - startTime
        Log.i(TAG, "Gemini generate completed for model=$model duration=${duration}ms success=${result.isSuccess}")
        return result
    }

    private suspend fun executeGenerateContent(
        model: String,
        token: String,
        request: LlmRequest,
        allowRetryAfterUnauthorized: Boolean
    ): Result<LlmResponse> {
        Log.i(
            TAG,
            "Gemini executeGenerateContent model=$model retryAllowed=$allowRetryAfterUnauthorized " +
                "systemInstruction=${!request.systemInstruction.isNullOrBlank()}"
        )
        val result = withContext(Dispatchers.IO) {
            apiClient.generateContent(
                model = model,
                accessToken = token,
                request = request
            )
        }.getOrElse { error ->
            val detail = TLSExpireResolver.resolveMessage(
                error = error,
                fallbackMessage = "Gemini request failed before a response was received"
            )
            Log.w(TAG, "Gemini request transport failure: $detail", error)
            return Result.failure(
                LlmError.Network(
                    detail = detail,
                    original = error
                )
            )
        }

        Log.i(
            TAG,
            "Gemini response received http=${result.responseCode} bodyLength=${result.responseBody.length}"
        )

        return when (result.responseCode) {
            HttpURLConnection.HTTP_OK -> parseSuccessResponse(result.responseBody)

            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                Log.w(TAG, "Gemini response unauthorized; attempting retry=$allowRetryAfterUnauthorized")
                if (!allowRetryAfterUnauthorized) {
                    Result.failure(
                        LlmError.ProviderFailure(
                            detail = "Gemini OAuth token was rejected after retry"
                        )
                    )
                } else {
                    retryAfterUnauthorized(model = model, staleToken = token, request = request)
                }
            }

            429 -> {
                Log.w(TAG, "Gemini response rate limited")
                Result.failure(
                    LlmError.RateLimited("Gemini request was rate limited")
                )
            }

            else -> {
                Log.w(
                    TAG,
                    "Gemini request failed http=${result.responseCode} body=${result.responseBody.take(500)}"
                )
                Result.failure(
                    LlmError.ProviderFailure(
                        detail = buildString {
                            append("Gemini request failed with HTTP ")
                            append(result.responseCode)
                            if (result.responseBody.isNotBlank()) {
                                append(": ")
                                append(result.responseBody.take(500))
                            }
                        }
                    )
                )
            }
        }
    }

    private suspend fun retryAfterUnauthorized(
        model: String,
        staleToken: String,
        request: LlmRequest
    ): Result<LlmResponse> {
        Log.i(TAG, "Gemini retryAfterUnauthorized starting for model=$model")
        val tokenProvider = accessTokenProvider ?: return Result.failure(
            LlmError.ProviderFailure("Gemini OAuth token provider is not configured")
        )

        tokenProvider.invalidateToken(staleToken).getOrElse { error ->
            return Result.failure(
                LlmError.ProviderFailure(
                    detail = "Failed to invalidate expired Gemini OAuth token",
                    original = error
                )
            )
        }

        val refreshedToken = tokenProvider.fetchToken().getOrElse { error ->
            return Result.failure(
                LlmError.ProviderFailure(
                    detail = "Failed to refresh Gemini OAuth token after 401",
                    original = error
                )
            )
        }

        Log.i(TAG, "Gemini retryAfterUnauthorized acquired refreshed token")

        return executeGenerateContent(
            model = model,
            token = refreshedToken,
            request = request,
            allowRetryAfterUnauthorized = false
        )
    }

    private fun parseSuccessResponse(responseBody: String): Result<LlmResponse> {
        return runCatching {
            val json = JSONObject(responseBody)
            val candidate = json.optJSONArray("candidates")
                ?.optJSONObject(0)
            val rawText = candidate
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.let(::extractPartsText)
                .orEmpty()

            LlmResponse(
                rawText = rawText,
                finishReason = candidate?.optString("finishReason")?.takeIf { it.isNotBlank() },
                usage = json.optJSONObject("usageMetadata")?.let(::parseUsageMetadata)
            )
        }.fold(
            onSuccess = {
                val usageInfo = it.usage?.let { u ->
                    "usage=[in:${u.inputTokens}, out:${u.outputTokens}, total:${u.totalTokens}]"
                } ?: "usage=unknown"
                Log.i(
                    TAG,
                    "Gemini response parsed successfully rawTextLength=${it.rawText.length} " +
                        "finishReason=${it.finishReason} $usageInfo rawTextPreview=${it.rawText.take(200)}"
                )
                Result.success(it)
            },
            onFailure = { error ->
                Log.w(TAG, "Gemini response parse failed body=${responseBody.take(500)}", error)
                Result.failure(
                    LlmError.InvalidResponse(
                        detail = "Failed to parse Gemini response payload",
                        original = error
                    )
                )
            }
        )
    }

    private fun extractPartsText(parts: org.json.JSONArray): String {
        return buildString {
            for (index in 0 until parts.length()) {
                val text = parts.optJSONObject(index)?.optString("text").orEmpty()
                if (text.isNotBlank()) {
                    append(text)
                }
            }
        }
    }

    private fun parseUsageMetadata(json: JSONObject): LlmUsageMetadata {
        return LlmUsageMetadata(
            inputTokens = json.optNullableInt("promptTokenCount"),
            outputTokens = json.optNullableInt("candidatesTokenCount"),
            totalTokens = json.optNullableInt("totalTokenCount")
        )
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }
}
