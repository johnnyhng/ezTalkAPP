package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import java.io.File

internal data class LocalGemmaModel(
    val name: String,
    val path: String
)

internal class LocalGemmaModelManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val baseDir = File(appContext.filesDir, "models/local_gemma")

    fun listModels(): List<LocalGemmaModel> {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        val models = mutableListOf<LocalGemmaModel>()
        baseDir.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.forEach { modelDir ->
                val modelFile = File(modelDir, MODEL_FILE_NAME)
                if (isUsableModelFile(modelFile)) {
                    models.add(LocalGemmaModel(name = modelDir.name, path = modelFile.absolutePath))
                }
            }

        val legacyFile = File(File(appContext.filesDir, "models/gemma4_e2b"), MODEL_FILE_NAME)
        if (isUsableModelFile(legacyFile) && models.none { it.name == LEGACY_MODEL_NAME }) {
            models.add(LocalGemmaModel(name = LEGACY_MODEL_NAME, path = legacyFile.absolutePath))
        }

        return models.sortedBy { it.name }
    }

    fun resolveModel(modelName: String): LocalGemmaModel? {
        val normalizedName = modelName.trim()
        if (normalizedName.isBlank()) return null

        val modelFile = getModelFile(normalizedName)
        return if (isUsableModelFile(modelFile)) {
            LocalGemmaModel(name = normalizedName, path = modelFile.absolutePath)
        } else {
            null
        }
    }

    fun getModelFile(modelName: String): File {
        return if (modelName == LEGACY_MODEL_NAME) {
            File(File(appContext.filesDir, "models/gemma4_e2b"), MODEL_FILE_NAME)
        } else {
            File(File(baseDir, modelName), MODEL_FILE_NAME)
        }
    }

    private fun isUsableModelFile(file: File): Boolean {
        return file.exists() && file.isFile && file.length() > MIN_MODEL_BYTES
    }

    private companion object {
        const val MODEL_FILE_NAME = "model.litertlm"
        const val LEGACY_MODEL_NAME = "gemma4_e2b"
        const val MIN_MODEL_BYTES = 1024L * 1024L
    }
}
