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

internal class LocalGemmaModelManager(
    private val context: Context
) {
    private val modelDir = File(context.filesDir, "models/gemma2_2b")
    val modelFile = File(modelDir, "model.bin")
    private val tempFile = File(modelDir, "model.bin.tmp")

    companion object {
        const val DEFAULT_GEMMA_URL = "https://huggingface.co/google/gemma-2-2b-it-cpu-int4/resolve/main/gemma-2-2b-it-cpu-int4.bin"
    }

    fun check(): SpeakerLocalLlmStatus {
        return if (modelFile.exists() && modelFile.length() > 100 * 1024 * 1024) {
            SpeakerLocalLlmStatus.Available
        } else {
            SpeakerLocalLlmStatus.Downloadable
        }
    }

    suspend fun download(
        urlStr: String = DEFAULT_GEMMA_URL,
        onStatus: (SpeakerLocalLlmStatus) -> Unit = {}
    ): SpeakerLocalLlmStatus = withContext(Dispatchers.IO) {
        try {
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }

            Log.i(TAG, "Starting Gemma 2 2B download from: $urlStr")
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext SpeakerLocalLlmStatus.Error("HTTP Server returned: ${connection.responseCode} ${connection.responseMessage}")
            }

            val fileLength = connection.contentLengthLong
            Log.i(TAG, "Gemma 2 2B file length: $fileLength bytes")

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
                Log.i(TAG, "Gemma 2 2B download completed successfully.")
                SpeakerLocalLlmStatus.Available
            } else {
                SpeakerLocalLlmStatus.Error("Downloaded file is empty")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Gemma 2 2B model", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            SpeakerLocalLlmStatus.Error(e.message ?: "Unknown download error")
        }
    }
}
