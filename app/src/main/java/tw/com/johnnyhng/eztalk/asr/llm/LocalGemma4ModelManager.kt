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

internal class LocalGemma4ModelManager(
    private val context: Context
) {
    private val modelDir = File(context.filesDir, "models/gemma4_e2b")
    val modelFile = File(modelDir, "model.litertlm")
    private val tempFile = File(modelDir, "model.litertlm.tmp")

    companion object {
        const val DEFAULT_GEMMA4_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-litert-lm.litertlm"
    }

    fun check(): SpeakerLocalLlmStatus {
        // Gemma 4 E2B is around 2.58 GB, so we check for at least 100 MB to verify presence
        return if (modelFile.exists() && modelFile.length() > 100 * 1024 * 1024) {
            SpeakerLocalLlmStatus.Available
        } else {
            SpeakerLocalLlmStatus.Downloadable
        }
    }

    suspend fun download(
        urlStr: String = DEFAULT_GEMMA4_URL,
        token: String = "",
        onStatus: (SpeakerLocalLlmStatus) -> Unit = {}
    ): SpeakerLocalLlmStatus = withContext(Dispatchers.IO) {
        try {
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }

            Log.i(TAG, "Starting Gemma 4 E2B download from: $urlStr")
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
            Log.i(TAG, "Gemma 4 E2B file length: $fileLength bytes")

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
                Log.i(TAG, "Gemma 4 E2B download completed successfully.")
                SpeakerLocalLlmStatus.Available
            } else {
                SpeakerLocalLlmStatus.Error("Downloaded file is empty")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Gemma 4 E2B model", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            SpeakerLocalLlmStatus.Error(e.message ?: "Unknown download error")
        }
    }

    suspend fun importModel(inputStream: java.io.InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
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
                Log.i(TAG, "Gemma 4 E2B model imported successfully.")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing Gemma 4 E2B model", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            false
        }
    }

    fun deleteModel(): Boolean {
        return try {
            if (modelFile.exists()) {
                modelFile.delete()
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }
            Log.i(TAG, "Gemma 4 E2B model deleted successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting Gemma 4 E2B model", e)
            false
        }
    }
}
