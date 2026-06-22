package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.TAG
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal data class LocalGemmaModel(
    val name: String,
    val path: String
)

internal class LocalGemmaModelManager(
    private val context: Context
) {
    private val baseDir = File(context.filesDir, "models/local_gemma")

    companion object {
        const val DEFAULT_LOCAL_GEMMA_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-litert-lm.litertlm"
    }

    fun listModels(): List<LocalGemmaModel> {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val list = mutableListOf<LocalGemmaModel>()

        // 1. Scan new layout directories
        val dirs = baseDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        dirs.forEach { modelDir ->
            val modelFile = File(modelDir, "model.litertlm")
            if (modelFile.exists() && modelFile.length() > 1024 * 1024) {
                list.add(LocalGemmaModel(modelDir.name, modelFile.absolutePath))
            }
        }

        // 2. Legacy fallback
        val legacyDir = File(context.filesDir, "models/gemma4_e2b")
        val legacyFile = File(legacyDir, "model.litertlm")
        if (legacyFile.exists() && legacyFile.length() > 1024 * 1024) {
            if (list.none { it.name == "gemma4_e2b" }) {
                list.add(LocalGemmaModel("gemma4_e2b", legacyFile.absolutePath))
            }
        }

        return list.sortedBy { it.name }
    }

    fun getModelFile(modelName: String): File {
        if (modelName == "gemma4_e2b") {
            val legacyDir = File(context.filesDir, "models/gemma4_e2b")
            return File(legacyDir, "model.litertlm")
        }
        val modelDir = File(baseDir, modelName)
        return File(modelDir, "model.litertlm")
    }

    fun check(modelName: String): SpeakerLocalLlmStatus {
        val modelFile = getModelFile(modelName)
        return if (modelFile.exists() && modelFile.length() > 1024 * 1024) {
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
        var tempFile: File? = null
        try {
            // Extract model name from URL
            val uri = android.net.Uri.parse(urlStr)
            var fileName = uri.lastPathSegment
            if (fileName.isNullOrBlank() || !fileName.endsWith(".litertlm")) {
                fileName = "downloaded_model.litertlm"
            }
            val modelName = fileName.removeSuffix(".litertlm")
            val modelDir = File(baseDir, modelName)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            val modelFile = File(modelDir, "model.litertlm")
            tempFile = File(modelDir, "model.litertlm.tmp")

            if (tempFile.exists()) {
                tempFile.delete()
            }

            Log.i(TAG, "Starting Local Gemma download from: $urlStr")
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            // Set Hugging Face authorization header if token is provided
            if (token.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext SpeakerLocalLlmStatus.Error("HTTP Server returned: ${connection.responseCode} ${connection.responseMessage}")
            }

            val fileLength = connection.contentLengthLong
            Log.i(TAG, "Local Gemma file length: $fileLength bytes")

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val data = ByteArray(1024 * 64)
                    var total: Long = 0
                    var count: Int
                    var lastUpdatePercent = -1L

                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)

                        if (fileLength > 0) {
                            val percent = (total * 100) / fileLength
                            if (percent != lastUpdatePercent) {
                                lastUpdatePercent = percent
                                onStatus(
                                    SpeakerLocalLlmStatus.Downloading(
                                        downloadedBytes = total,
                                        totalBytes = fileLength
                                    )
                                )
                            }
                        } else {
                            onStatus(
                                SpeakerLocalLlmStatus.Downloading(
                                    downloadedBytes = total,
                                    totalBytes = null
                                )
                            )
                        }
                    }
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (modelFile.exists()) {
                    modelFile.delete()
                }
                tempFile.renameTo(modelFile)
                Log.i(TAG, "Local Gemma download completed successfully: $modelName")
                SpeakerLocalLlmStatus.Available
            } else {
                SpeakerLocalLlmStatus.Error("Downloaded file is empty")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Local Gemma model", e)
            if (tempFile?.exists() == true) {
                tempFile.delete()
            }
            SpeakerLocalLlmStatus.Error(e.message ?: "Unknown download error")
        }
    }

    suspend fun importModel(inputStream: java.io.InputStream, displayName: String): Boolean = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            var fileName = displayName
            if (fileName.isBlank() || !fileName.endsWith(".litertlm")) {
                fileName = "imported_model.litertlm"
            }
            val modelName = fileName.removeSuffix(".litertlm")
            val modelDir = File(baseDir, modelName)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            val modelFile = File(modelDir, "model.litertlm")
            tempFile = File(modelDir, "model.litertlm.tmp")

            if (tempFile.exists()) {
                tempFile.delete()
            }
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                if (modelFile.exists()) {
                    modelFile.delete()
                }
                tempFile.renameTo(modelFile)
                Log.i(TAG, "Local Gemma model imported successfully: $modelName")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing Local Gemma model", e)
            if (tempFile?.exists() == true) {
                tempFile.delete()
            }
            false
        }
    }

    fun deleteModel(modelName: String): Boolean {
        return try {
            val modelDir = if (modelName == "gemma4_e2b") {
                File(context.filesDir, "models/gemma4_e2b")
            } else {
                File(baseDir, modelName)
            }
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
            }
            Log.i(TAG, "Local Gemma model deleted successfully: $modelName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting Local Gemma model: $modelName", e)
            false
        }
    }
}
