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

class TseModelManagerTest {
    private val context: Context = object : ContextWrapper(null) {
        private val filesDir = TestFixtures.tempDir("tse-model-context")

        override fun getFilesDir(): File = filesDir
    }

    @Test
    fun listModelsReturnsOnlyCompleteModelDirectories() {
        val userId = "tse-model-filtering"
        val userDir = userTseModelsDir(userId).apply {
            deleteRecursively()
            mkdirs()
        }
        createTseModelDir(userDir, "complete-a", withModel = true, withDvector = true)
        createTseModelDir(userDir, "missing-model", withModel = false, withDvector = true)
        createTseModelDir(userDir, "missing-dvector", withModel = true, withDvector = false)

        val models = TseModelManager.listModels(context, userId)

        assertEquals(1, models.size)
        assertEquals("complete-a", models.first().name)
    }

    @Test
    fun getModelReturnsNamedModelOrFallsBackToFirstAvailable() {
        val userId = "tse-model-selection"
        val userDir = userTseModelsDir(userId).apply {
            deleteRecursively()
            mkdirs()
        }
        createTseModelDir(userDir, "first-model", withModel = true, withDvector = true)
        createTseModelDir(userDir, "second-model", withModel = true, withDvector = true)

        val named = TseModelManager.getModel(context, userId, "second-model")
        val fallback = TseModelManager.getModel(context, userId, "missing-model")

        assertNotNull(named)
        assertEquals("second-model", named?.name)
        assertNotNull(fallback)
        assertEquals("first-model", fallback?.name)
    }

    @Test
    fun deleteModelDeletesNonDefaultModelDirectory() {
        val userId = "tse-model-delete"
        val userDir = userTseModelsDir(userId).apply {
            deleteRecursively()
            mkdirs()
        }
        createTseModelDir(userDir, "custom-model", withModel = true, withDvector = true)

        val deleted = TseModelManager.deleteModel(context, userId, "custom-model")

        assertTrue(deleted)
        assertFalse(File(userDir, "custom-model").exists())
    }

    @Test
    fun deleteModelAllowsDeletingDefaultWhenAnotherModelExists() {
        val userId = "tse-model-default-delete"
        val userDir = userTseModelsDir(userId).apply {
            deleteRecursively()
            mkdirs()
        }
        createTseModelDir(userDir, "default", withModel = true, withDvector = true)
        createTseModelDir(userDir, "custom-model", withModel = true, withDvector = true)

        val deleted = TseModelManager.deleteModel(context, userId, "default")

        assertTrue(deleted)
        assertFalse(File(userDir, "default").exists())
    }

    @Test
    fun deleteModelReturnsFalseWhenDirectoryDoesNotExist() {
        val deleted = TseModelManager.deleteModel(context, "tse-model-missing-delete", "missing")

        assertFalse(deleted)
    }

    @Test
    fun getModelReturnsNullWhenNoValidModelsExistAfterCopyAttempt() {
        val userId = "tse-model-empty"
        val userDir = userTseModelsDir(userId).apply {
            deleteRecursively()
            mkdirs()
        }
        File(userDir, "incomplete").mkdirs()

        val model = TseModelManager.getModel(context, userId, "missing")

        assertNull(model)
    }

    private fun userTseModelsDir(userId: String): File =
        File(context.filesDir, "tse_models/$userId")

    private fun createTseModelDir(
        parent: File,
        name: String,
        withModel: Boolean,
        withDvector: Boolean
    ): File {
        val dir = File(parent, name).apply { mkdirs() }
        if (withModel) {
            File(dir, "model.onnx").writeText("model")
        }
        if (withDvector) {
            File(dir, "dvector.bin").writeText("dvector")
        }
        return dir
    }
}
