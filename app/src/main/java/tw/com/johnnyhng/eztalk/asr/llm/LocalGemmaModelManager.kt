package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LocalGemmaModel(
    val name: String,
    val path: String
)

internal class LocalGemmaModelManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val baseDir = File(appContext.filesDir, "models/local_gemma")

    companion object {
        const val CLOUD_FALLBACK_MODEL_NAME = ""
        const val DEFAULT_LOCAL_GEMMA_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-litert-lm.litertlm"
        private const val MODEL_FILE_NAME = "model.litertlm"
        private const val LEGACY_MODEL_NAME = "gemma4_e2b"
        private const val MIN_MODEL_BYTES = 1024L * 1024L
    }

    fun listModels(): List<LocalGemmaModel> {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        val models = mutableListOf(LocalGemmaModel(name = CLOUD_FALLBACK_MODEL_NAME, path = ""))
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

    fun check(modelName: String): SpeakerLocalLlmStatus {
        if (modelName.isBlank()) return SpeakerLocalLlmStatus.CloudFallback
        return if (isUsableModelFile(getModelFile(modelName))) {
            SpeakerLocalLlmStatus.Available
        } else {
            SpeakerLocalLlmStatus.Downloadable
        }
    }

    suspend fun download(
        urlStr: String = DEFAULT_LOCAL_GEMMA_URL,
        token: String = "",
        onStatus: (SpeakerLocalLlmStatus) -> Unit = {}
    ): SpeakerLocalLlmStatus = withContext(Dispatchers.IO) {
        val downloadUrl = urlStr.ifBlank { DEFAULT_LOCAL_GEMMA_URL }
        var tempFile: File? = null
        try {
            val uri = Uri.parse(downloadUrl)
            val fileName = uri.lastPathSegment
                ?.takeIf { it.endsWith(".litertlm", ignoreCase = true) }
                ?: "downloaded_model.litertlm"
            val modelName = fileName.removeSuffix(".litertlm")
            val modelDir = File(baseDir, modelName).apply { mkdirs() }
            val modelFile = File(modelDir, MODEL_FILE_NAME)
            tempFile = File(modelDir, "$MODEL_FILE_NAME.tmp").also {
                if (it.exists()) it.delete()
            }

            val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                if (token.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                connect()
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext SpeakerLocalLlmStatus.Error(
                    "HTTP ${connection.responseCode} ${connection.responseMessage}"
                )
            }

            val fileLength = connection.contentLengthLong.takeIf { it > 0L }
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(tempFile).use { output ->
                    copyWithProgress(
                        input = input,
                        output = output,
                        totalBytes = fileLength,
                        onStatus = onStatus
                    )
                }
            }

            if (!isUsableModelFile(tempFile)) {
                tempFile.delete()
                return@withContext SpeakerLocalLlmStatus.Error("Downloaded file is too small or empty")
            }

            if (modelFile.exists()) modelFile.delete()
            if (!tempFile.renameTo(modelFile)) {
                return@withContext SpeakerLocalLlmStatus.Error("Failed to move downloaded model into place")
            }
            SpeakerLocalLlmStatus.Available
        } catch (error: Exception) {
            tempFile?.delete()
            SpeakerLocalLlmStatus.Error(error.message ?: error.javaClass.simpleName)
        }
    }

    suspend fun importModel(
        inputStream: InputStream,
        displayName: String
    ): Boolean = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            val fileName = displayName
                .takeIf { it.endsWith(".litertlm", ignoreCase = true) }
                ?: "imported_model.litertlm"
            val modelName = fileName.removeSuffix(".litertlm")
            val modelDir = File(baseDir, modelName).apply { mkdirs() }
            val modelFile = File(modelDir, MODEL_FILE_NAME)
            tempFile = File(modelDir, "$MODEL_FILE_NAME.tmp").also {
                if (it.exists()) it.delete()
            }

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!isUsableModelFile(tempFile)) {
                tempFile.delete()
                return@withContext false
            }

            if (modelFile.exists()) modelFile.delete()
            tempFile.renameTo(modelFile)
        } catch (_: Exception) {
            tempFile?.delete()
            false
        }
    }

    fun deleteModel(modelName: String): Boolean {
        if (modelName.isBlank()) return false
        return try {
            val modelDir = if (modelName == LEGACY_MODEL_NAME) {
                File(appContext.filesDir, "models/gemma4_e2b")
            } else {
                File(baseDir, modelName)
            }
            !modelDir.exists() || modelDir.deleteRecursively()
        } catch (_: Exception) {
            false
        }
    }

    private fun copyWithProgress(
        input: InputStream,
        output: FileOutputStream,
        totalBytes: Long?,
        onStatus: (SpeakerLocalLlmStatus) -> Unit
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copiedBytes = 0L
        var count: Int
        var lastReportedPercent = -1L
        while (input.read(buffer).also { count = it } != -1) {
            output.write(buffer, 0, count)
            copiedBytes += count

            if (totalBytes != null) {
                val percent = copiedBytes * 100L / totalBytes
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    onStatus(
                        SpeakerLocalLlmStatus.Downloading(
                            downloadedBytes = copiedBytes,
                            totalBytes = totalBytes
                        )
                    )
                }
            } else {
                onStatus(
                    SpeakerLocalLlmStatus.Downloading(
                        downloadedBytes = copiedBytes,
                        totalBytes = null
                    )
                )
            }
        }
    }

    private fun isUsableModelFile(file: File): Boolean {
        return file.exists() && file.isFile && file.length() > MIN_MODEL_BYTES
    }
}
