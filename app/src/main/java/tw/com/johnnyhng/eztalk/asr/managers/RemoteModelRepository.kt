package tw.com.johnnyhng.eztalk.asr.managers

import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.utils.downloadModelFileResult
import tw.com.johnnyhng.eztalk.asr.utils.listRemoteModelsResult
import java.io.File

data class RemoteModelDescriptor(
    val name: String,
    val filename: String,
    val fileSizeBytes: Long,
    val serverHash: String,
    val updateAvailable: Boolean = false
)

data class RemoteModelListFetchResult(
    val models: List<RemoteModelDescriptor>,
    val errorMessage: String? = null
)

interface RemoteModelRepository {
    suspend fun listRemoteModels(
        modelApiBaseUrl: String,
        userId: String,
        selectedModelName: String,
        allowInsecureTls: Boolean
    ): RemoteModelListFetchResult

    suspend fun downloadModel(
        modelApiBaseUrl: String,
        userId: String,
        remoteModel: RemoteModelDescriptor,
        userModelsDir: File,
        allowInsecureTls: Boolean,
        onProgress: (Float?) -> Unit
    ): Result<File>
}

object DirectUrlRemoteModelRepository : RemoteModelRepository {
    override suspend fun listRemoteModels(
        modelApiBaseUrl: String,
        userId: String,
        selectedModelName: String,
        allowInsecureTls: Boolean
    ): RemoteModelListFetchResult {
        if (modelApiBaseUrl.trim().isBlank() || userId.trim().isBlank()) {
            return RemoteModelListFetchResult(emptyList())
        }

        val result = listRemoteModelsResult(
            baseUrl = modelApiBaseUrl,
            userId = userId,
            allowInsecureTls = allowInsecureTls
        )
        return RemoteModelListFetchResult(
            models = result.models.map { modelName ->
                RemoteModelDescriptor(
                    name = modelName,
                    filename = "model.int8.onnx",
                    fileSizeBytes = 0L,
                    serverHash = ""
                )
            },
            errorMessage = result.errorMessage
        )
    }

    override suspend fun downloadModel(
        modelApiBaseUrl: String,
        userId: String,
        remoteModel: RemoteModelDescriptor,
        userModelsDir: File,
        allowInsecureTls: Boolean,
        onProgress: (Float?) -> Unit
    ): Result<File> = runCatching {
        val targetDir = File(userModelsDir, remoteModel.name)
        targetDir.mkdirs()
        val modelFile = File(targetDir, remoteModel.filename)
        val tokensFile = File(targetDir, "tokens.txt")

        val modelDownload = downloadModelFileResult(
            baseUrl = modelApiBaseUrl,
            userId = userId,
            modelName = remoteModel.name,
            filename = remoteModel.filename,
            targetFile = modelFile,
            allowInsecureTls = allowInsecureTls,
            onProgress = onProgress
        )
        if (!modelDownload.success) {
            error(modelDownload.errorMessage ?: "Failed to download ${remoteModel.filename}")
        }

        val tokensDownload = downloadModelFileResult(
            baseUrl = modelApiBaseUrl,
            userId = userId,
            modelName = remoteModel.name,
            filename = "tokens.txt",
            targetFile = tokensFile,
            allowInsecureTls = allowInsecureTls
        )
        if (!tokensDownload.success) {
            error(tokensDownload.errorMessage ?: "Failed to download tokens.txt")
        }

        onProgress(null)
        targetDir
    }.onFailure { error ->
        Log.e(TAG, "Remote model download failed: ${remoteModel.name}", error)
    }
}
