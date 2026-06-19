package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LocalGemmaMediapipeLlmProviderTest {
    private val context: Context = object : ContextWrapper(null) {
        private val filesDir = TestFixtures.tempDir("gemma-provider-context")

        override fun getFilesDir(): File = filesDir
    }

    @Test
    fun buildPromptTextFormatsGemmaTemplateCorrectly() {
        val provider = LocalGemmaMediapipeLlmProvider(context)
        val request = LlmRequest(
            model = "gemma2",
            systemInstruction = "You are a helpful assistant.",
            userPrompt = "Hello!",
            outputFormat = LlmOutputFormat.TEXT,
            schemaHint = "schema"
        )

        val formatted = provider.buildPromptText(request)

        val expected = "<start_of_turn>user\n" +
                "You are a helpful assistant.\n\n" +
                "Hello!\n\n" +
                "Return JSON matching this schema hint:\n" +
                "schema" +
                "<end_of_turn>\n<start_of_turn>model\n"

        assertEquals(expected, formatted)
    }

    @Test
    fun generateReturnsFailureWhenModelFileNotFound() {
        val provider = LocalGemmaMediapipeLlmProvider(context)
        val request = LlmRequest(
            model = "gemma2",
            systemInstruction = null,
            userPrompt = "Hello",
            outputFormat = LlmOutputFormat.TEXT,
            schemaHint = null
        )

        // Run blocking because it will try to initialize LlmInference and fail due to model.bin not existing.
        // We want to verify that the failure is wrapped as a Result.failure.
        val result = kotlinx.coroutines.runBlocking {
            provider.generate(request)
        }

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is LlmError.ProviderFailure)
    }
}
