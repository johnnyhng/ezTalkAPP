package tw.com.johnnyhng.eztalk.asr.managers

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.fixtures.TestFixtures
import java.io.File

class ModelManagerTest {
    private val context: Context = object : ContextWrapper(null) {
        private val filesDir = TestFixtures.tempDir("model-context")

        override fun getFilesDir(): File = filesDir
    }

    @Test
    fun listModelsReturnsOnlyCompleteModelDirectories() {
        val userId = "model-filtering"
        val userDir = userModelsDir(userId).apply {
            deleteRecursively()
            mkdirs()
        }
        createModelDir(userDir, "complete-a", withModel = true, withTokens = true)
        createModelDir(userDir, "missing-model", withModel = false, withTokens = true)
        createModelDir(userDir, "missing-tokens", withModel = true, withTokens = false)

        val models = ModelManager.listModels(context, userId)

        assertEquals(1, models.size)
        assertEquals("complete-a", models.first().name)
    }

    @Test
    fun getModelReturnsNamedModelOrFallsBackToFirstAvailable() {
        val userId = "model-selection"
        val userDir = userModelsDir(userId).apply {
            deleteRecursively()
            mkdirs()
        }
        createModelDir(userDir, "first-model", withModel = true, withTokens = true)
        createModelDir(userDir, "second-model", withModel = true, withTokens = true)

        val named = ModelManager.getModel(context, userId, "second-model")
        val fallback = ModelManager.getModel(context, userId, "missing-model")

        assertNotNull(named)
        assertEquals("second-model", named?.name)
        assertNotNull(fallback)
        assertEquals("first-model", fallback?.name)
    }

    @Test
    fun deleteModelDeletesNonDefaultModelDirectory() {
        val userId = "model-delete"
        val userDir = userModelsDir(userId).apply {
            deleteRecursively()
            mkdirs()
        }
        createModelDir(userDir, "custom-model", withModel = true, withTokens = true)

        val deleted = ModelManager.deleteModel(context, userId, "custom-model")

        assertTrue(deleted)
        assertFalse(File(userDir, "custom-model").exists())
    }

    @Test
    fun deleteModelAllowsDeletingDefaultWhenAnotherModelExists() {
        val userId = "model-default-delete"
        val userDir = userModelsDir(userId).apply {
            deleteRecursively()
            mkdirs()
        }
        createModelDir(userDir, "custom-sense-voice", withModel = true, withTokens = true)
        createModelDir(userDir, "custom-model", withModel = true, withTokens = true)

        val deleted = ModelManager.deleteModel(context, userId, "custom-sense-voice")

        assertTrue(deleted)
        assertFalse(File(userDir, "custom-sense-voice").exists())
    }

    @Test
    fun deleteModelReturnsFalseWhenDirectoryDoesNotExist() {
        val deleted = ModelManager.deleteModel(context, "model-missing-delete", "missing")

        assertFalse(deleted)
    }

    @Test
    fun getModelReturnsNullWhenNoValidModelsExistAfterCopyAttempt() {
        val userId = "model-empty"
        val userDir = userModelsDir(userId).apply {
            deleteRecursively()
            mkdirs()
        }
        File(userDir, "incomplete").mkdirs()

        val model = ModelManager.getModel(context, userId, "missing")

        assertNull(model)
    }

    private fun userModelsDir(userId: String): File =
        File(context.filesDir, "models/$userId")

    private fun createModelDir(
        parent: File,
        name: String,
        withModel: Boolean,
        withTokens: Boolean
    ): File {
        val dir = File(parent, name).apply { mkdirs() }
        if (withModel) {
            File(dir, "model.int8.onnx").writeText("model")
        }
        if (withTokens) {
            File(dir, "tokens.txt").writeText("tokens")
        }
        return dir
    }
}
