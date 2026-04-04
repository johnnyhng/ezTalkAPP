package tw.com.johnnyhng.eztalk.asr.managers

import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.utils.downloadModelFile
import tw.com.johnnyhng.eztalk.asr.utils.listRemoteModels
import java.io.File

data class RemoteModelDescriptor(
    val name: String,
    val filename: String,
    val fileSizeBytes: Long,
    val serverHash: String
)

interface RemoteModelRepository {
    suspend fun listRemoteModels(
        modelApiBaseUrl: String,
        userId: String,
        selectedModelName: String
    ): List<RemoteModelDescriptor>

    suspend fun downloadModel(
        modelApiBaseUrl: String,
        userId: String,
        remoteModel: RemoteModelDescriptor,
        userModelsDir: File,
        onProgress: (Float?) -> Unit
    ): Result<File>
}

object DirectUrlRemoteModelRepository : RemoteModelRepository {
    override suspend fun listRemoteModels(
        modelApiBaseUrl: String,
        userId: String,
        selectedModelName: String
    ): List<RemoteModelDescriptor> {
        if (modelApiBaseUrl.trim().isBlank() || userId.trim().isBlank()) return emptyList()

        return listRemoteModels(
            baseUrl = modelApiBaseUrl,
            userId = userId
        ).map { modelName ->
            RemoteModelDescriptor(
                name = modelName,
                filename = "model.int8.onnx",
                fileSizeBytes = 0L,
                serverHash = ""
            )
        }
    }

    override suspend fun downloadModel(
        modelApiBaseUrl: String,
        userId: String,
        remoteModel: RemoteModelDescriptor,
        userModelsDir: File,
        onProgress: (Float?) -> Unit
    ): Result<File> = runCatching {
        val targetDir = File(userModelsDir, remoteModel.name)
        targetDir.mkdirs()
        val modelFile = File(targetDir, remoteModel.filename)
        val tokensFile = File(targetDir, "tokens.txt")

        if (!downloadModelFile(
                baseUrl = modelApiBaseUrl,
                userId = userId,
                modelName = remoteModel.name,
                filename = remoteModel.filename,
                targetFile = modelFile,
                onProgress = onProgress
            )
        ) {
            error("Failed to download ${remoteModel.filename}")
        }

        if (!downloadModelFile(
                baseUrl = modelApiBaseUrl,
                userId = userId,
                modelName = remoteModel.name,
                filename = "tokens.txt",
                targetFile = tokensFile
            )
        ) {
            error("Failed to download tokens.txt")
        }

        onProgress(null)
        targetDir
    }.onFailure { error ->
        Log.e(TAG, "Remote model download failed: ${remoteModel.name}", error)
    }
}
