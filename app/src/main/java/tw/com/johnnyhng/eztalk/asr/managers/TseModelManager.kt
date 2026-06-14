package tw.com.johnnyhng.eztalk.asr.managers

import android.content.Context
import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.data.classes.TseModel
import java.io.File
import java.io.FileOutputStream

object TseModelManager {
    private const val DEFAULT_TSE_MODEL_DIR = "default"
    private const val DEFAULT_TSE_MODEL_ASSET = "transformer_energy_64d_1L_int8.onnx"
    private const val DEFAULT_DVECTOR_ASSET = "dvector.bin"

    fun listModels(context: Context, userId: String): List<TseModel> {
        val userModelsDir = File(context.filesDir, "tse_models/$userId")
        if (!userModelsDir.exists()) {
            userModelsDir.mkdirs()
        }

        val userModels = userModelsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (userModels.isEmpty()) {
            copyDefaultModelFromAssets(context, userId)
        }

        return userModelsDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { modelDir ->
            val modelFile = File(modelDir, "model.onnx")
            val dvectorFile = File(modelDir, "dvector.bin")
            if (modelFile.exists() && dvectorFile.exists()) {
                TseModel(modelDir.name, modelFile.absolutePath, dvectorFile.absolutePath)
            } else {
                null
            }
        } ?: emptyList()
    }

    fun getModel(context: Context, userId: String, modelName: String?): TseModel? {
        val models = listModels(context, userId)
        return models.firstOrNull { it.name == modelName } ?: models.firstOrNull { it.name == DEFAULT_TSE_MODEL_DIR } ?: models.firstOrNull()
    }

    private fun copyDefaultModelFromAssets(context: Context, userId: String) {
        val targetDir = File(context.filesDir, "tse_models/$userId/$DEFAULT_TSE_MODEL_DIR")
        if (targetDir.exists()) return
        targetDir.mkdirs()

        try {
            val assetManager = context.assets

            assetManager.open(DEFAULT_TSE_MODEL_ASSET).use { input ->
                FileOutputStream(File(targetDir, "model.onnx")).use { output ->
                    input.copyTo(output)
                }
            }

            assetManager.open(DEFAULT_DVECTOR_ASSET).use { input ->
                FileOutputStream(File(targetDir, "dvector.bin")).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(
                TAG,
                "Copied default TSE model '$DEFAULT_TSE_MODEL_DIR' from assets for user $userId"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error copying default TSE model from assets", e)
        }
    }

    fun deleteModel(context: Context, userId: String, modelName: String): Boolean {
        if (modelName == DEFAULT_TSE_MODEL_DIR && listModels(context, userId).size == 1) {
            Log.w(TAG, "Cannot delete the last default TSE model.")
            return false
        }
        val modelDir = File(context.filesDir, "tse_models/$userId/$modelName")
        return if (modelDir.exists() && modelDir.isDirectory) {
            modelDir.deleteRecursively()
        } else {
            false
        }
    }
}
