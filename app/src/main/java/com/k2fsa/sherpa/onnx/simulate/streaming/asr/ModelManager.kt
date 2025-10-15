package com.k2fsa.sherpa.onnx.simulate.streaming.asr

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.data.classes.Model
import java.io.File
import java.io.FileOutputStream

object ModelManager {
    private const val DEFAULT_MODEL_DIR = "custom-sense-voice"

    fun listModels(context: Context, userId: String): List<Model> {
        val userModelsDir = File(context.filesDir, "models/$userId")
        if (!userModelsDir.exists()) {
            userModelsDir.mkdirs()
        }

        val userModels = userModelsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (userModels.isEmpty()) {
            copyDefaultModelFromAssets(context, userId)
        }

        return userModelsDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { modelDir ->
            val modelFile = File(modelDir, "model.int8.onnx")
            val tokensFile = File(modelDir, "tokens.txt")
            if (modelFile.exists() && tokensFile.exists()) {
                Model(modelDir.name, modelFile.absolutePath, tokensFile.absolutePath)
            } else {
                null
            }
        } ?: emptyList()
    }

    fun getModel(context: Context, userId: String, modelName: String?): Model? {
        val models = listModels(context, userId)
        return models.firstOrNull { it.name == modelName } ?: models.firstOrNull()
    }

    private fun copyDefaultModelFromAssets(context: Context, userId: String) {
        val targetDir = File(context.filesDir, "models/$userId/$DEFAULT_MODEL_DIR")
        if (targetDir.exists()) return
        targetDir.mkdirs()

        try {
            val assetManager = context.assets
            val modelAssetPath = "$DEFAULT_MODEL_DIR/model.int8.onnx"
            val tokensAssetPath = "$DEFAULT_MODEL_DIR/tokens.txt"

            assetManager.open(modelAssetPath).use { input ->
                FileOutputStream(File(targetDir, "model.int8.onnx")).use { output ->
                    input.copyTo(output)
                }
            }

            assetManager.open(tokensAssetPath).use { input ->
                FileOutputStream(File(targetDir, "tokens.txt")).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Copied default model '$DEFAULT_MODEL_DIR' for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying default model from assets", e)
        }
    }

    fun deleteModel(context: Context, userId: String, modelName: String): Boolean {
        if (modelName == DEFAULT_MODEL_DIR && listModels(context, userId).size == 1) {
            Log.w(TAG, "Cannot delete the last default model.")
            return false
        }
        val modelDir = File(context.filesDir, "models/$userId/$modelName")
        return if (modelDir.exists() && modelDir.isDirectory) {
            modelDir.deleteRecursively()
        } else {
            false
        }
    }
}
