package tw.com.johnnyhng.eztalk.asr.llm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection

class GeminiLlmProviderTest {
    @Test
    fun generate_returnsParsedResponseOnSuccess() = runBlocking {
        val tokenProvider = FakeTokenProvider(tokens = ArrayDeque(listOf("token-1")))
        val apiClient = FakeGeminiApiClient(
            results = ArrayDeque(
                listOf(
                    Result.success(
                        GeminiApiResult(
                            responseCode = HttpURLConnection.HTTP_OK,
                            responseBody = """
                                {
                                  "candidates": [
                                    {
                                      "finishReason": "STOP",
                                      "content": {
                                        "parts": [
                                          {"text": "{\"decision\":\"candidate\",\"lineStart\":1,\"lineEnd\":1}"}
                                        ]
                                      }
                                    }
                                  ],
                                  "usageMetadata": {
                                    "promptTokenCount": 10,
                                    "candidatesTokenCount": 5,
                                    "totalTokenCount": 15
                                  }
                                }
                            """.trimIndent()
                        )
                    )
                )
            )
        )
        val provider = GeminiLlmProvider(
            accessTokenProvider = tokenProvider,
            apiClient = apiClient
        )

        val result = provider.generate(sampleRequest())

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("""{"decision":"candidate","lineStart":1,"lineEnd":1}""", response.rawText)
        assertEquals("STOP", response.finishReason)
        assertEquals(15, response.usage?.totalTokens)
        assertEquals(listOf("token-1"), apiClient.seenTokens)
    }

    @Test
    fun generate_retriesOnceAfterUnauthorized() = runBlocking {
        val tokenProvider = FakeTokenProvider(tokens = ArrayDeque(listOf("stale-token", "fresh-token")))
        val apiClient = FakeGeminiApiClient(
            results = ArrayDeque(
                listOf(
                    Result.success(
                        GeminiApiResult(
                            responseCode = HttpURLConnection.HTTP_UNAUTHORIZED,
                            responseBody = """{"error":{"message":"expired"}}"""
                        )
                    ),
                    Result.success(
                        GeminiApiResult(
                            responseCode = HttpURLConnection.HTTP_OK,
                            responseBody = """
                                {
                                  "candidates": [
                                    {
                                      "content": {
                                        "parts": [
                                          {"text": "{\"decision\":\"autoplay\",\"lineStart\":2,\"lineEnd\":2}"}
                                        ]
                                      }
                                    }
                                  ]
                                }
                            """.trimIndent()
                        )
                    )
                )
            )
        )
        val provider = GeminiLlmProvider(
            accessTokenProvider = tokenProvider,
            apiClient = apiClient
        )

        val result = provider.generate(sampleRequest())

        assertTrue(result.isSuccess)
        assertEquals(listOf("stale-token"), tokenProvider.invalidatedTokens)
        assertEquals(listOf("stale-token", "fresh-token"), apiClient.seenTokens)
    }

    @Test
    fun generate_returnsInvalidResponseErrorForMalformedPayload() = runBlocking {
        val tokenProvider = FakeTokenProvider(tokens = ArrayDeque(listOf("token-1")))
        val apiClient = FakeGeminiApiClient(
            results = ArrayDeque(
                listOf(
                    Result.success(
                        GeminiApiResult(
                            responseCode = HttpURLConnection.HTTP_OK,
                            responseBody = """{"candidates":[{"content":{"parts":[]}}]}"""
                        )
                    )
                )
            )
        )
        val provider = GeminiLlmProvider(
            accessTokenProvider = tokenProvider,
            apiClient = apiClient
        )

        val result = provider.generate(sampleRequest())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is LlmError.InvalidResponse)
    }

    private fun sampleRequest(): LlmRequest {
        return LlmRequest(
            model = "gemini-2.5-flash",
            systemInstruction = "Pick one candidate.",
            userPrompt = "play weather report",
            outputFormat = LlmOutputFormat.JSON
        )
    }

    private class FakeTokenProvider(
        private val tokens: ArrayDeque<String>
    ) : GeminiAccessTokenProvider {
        val invalidatedTokens = mutableListOf<String>()

        override suspend fun fetchToken(): Result<String> {
            val token = tokens.removeFirstOrNull()
                ?: return Result.failure(IllegalStateException("No fake token available"))
            return Result.success(token)
        }

        override suspend fun invalidateToken(token: String): Result<Unit> {
            invalidatedTokens += token
            return Result.success(Unit)
        }
    }

    private class FakeGeminiApiClient(
        private val results: ArrayDeque<Result<GeminiApiResult>>
    ) : GeminiApiClient {
        val seenTokens = mutableListOf<String>()

        override fun generateContent(
            model: String,
            accessToken: String,
            request: LlmRequest
        ): Result<GeminiApiResult> {
            seenTokens += accessToken
            return results.removeFirstOrNull()
                ?: Result.failure(IllegalStateException("No fake API result available"))
        }
    }
}
