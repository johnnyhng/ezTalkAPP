package tw.com.johnnyhng.eztalk.asr.managers

import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.utils.listRemoteTseModelsResult
import tw.com.johnnyhng.eztalk.asr.utils.downloadTseModelFileResult
import java.io.File

interface RemoteTseModelRepository {
    suspend fun listRemoteTseModels(
        modelApiBaseUrl: String,
        userId: String,
        selectedModelName: String,
        allowInsecureTls: Boolean
    ): RemoteModelListFetchResult

    suspend fun downloadTseModel(
        modelApiBaseUrl: String,
        userId: String,
        remoteModel: RemoteModelDescriptor,
        userModelsDir: File,
        allowInsecureTls: Boolean,
        onProgress: (Float?) -> Unit
    ): Result<File>
}

object DirectUrlRemoteTseModelRepository : RemoteTseModelRepository {
    override suspend fun listRemoteTseModels(
        modelApiBaseUrl: String,
        userId: String,
        selectedModelName: String,
        allowInsecureTls: Boolean
    ): RemoteModelListFetchResult {
        if (modelApiBaseUrl.trim().isBlank() || userId.trim().isBlank()) {
            return RemoteModelListFetchResult(emptyList())
        }

        val result = listRemoteTseModelsResult(
            baseUrl = modelApiBaseUrl,
            userId = userId,
            allowInsecureTls = allowInsecureTls
        )
        return RemoteModelListFetchResult(
            models = result.models.map { modelName ->
                RemoteModelDescriptor(
                    name = modelName,
                    filename = "model.onnx",
                    fileSizeBytes = 0L,
                    serverHash = ""
                )
            },
            errorMessage = result.errorMessage
        )
    }

    override suspend fun downloadTseModel(
        modelApiBaseUrl: String,
        userId: String,
        remoteModel: RemoteModelDescriptor,
        userModelsDir: File,
        allowInsecureTls: Boolean,
        onProgress: (Float?) -> Unit
    ): Result<File> = runCatching {
        val targetDir = File(userModelsDir, remoteModel.name)
        targetDir.mkdirs()
        val modelFile = File(targetDir, "model.onnx")
        val dvectorFile = File(targetDir, "dvector.bin")

        val modelDownload = downloadTseModelFileResult(
            baseUrl = modelApiBaseUrl,
            userId = userId,
            modelName = remoteModel.name,
            filename = "model.onnx",
            targetFile = modelFile,
            allowInsecureTls = allowInsecureTls,
            onProgress = { p -> if (p != null) onProgress(p * 0.5f) else onProgress(null) }
        )
        if (!modelDownload.success) {
            error(modelDownload.errorMessage ?: "Failed to download model.onnx")
        }

        val dvectorDownload = downloadTseModelFileResult(
            baseUrl = modelApiBaseUrl,
            userId = userId,
            modelName = remoteModel.name,
            filename = "dvector.bin",
            targetFile = dvectorFile,
            allowInsecureTls = allowInsecureTls,
            onProgress = { p -> if (p != null) onProgress(0.5f + p * 0.5f) else onProgress(null) }
        )
        if (!dvectorDownload.success) {
            error(dvectorDownload.errorMessage ?: "Failed to download dvector.bin")
        }

        onProgress(null)
        targetDir
    }.onFailure { error ->
        Log.e(TAG, "Remote TSE model download failed: ${remoteModel.name}", error)
    }
}
