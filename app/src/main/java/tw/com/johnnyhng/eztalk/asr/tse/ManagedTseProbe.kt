package tw.com.johnnyhng.eztalk.asr.tse

import android.content.Context
import android.util.Log
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.java.TfLite
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import tw.com.johnnyhng.eztalk.asr.TAG
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Small managed-runtime feasibility probe for the TFLite VoiceFilter-Lite model.
 *
 * This class is intentionally not wired into the live TSE path yet.
 * It only proves whether the app can:
 * - initialize LiteRT from Google Play services
 * - map the .tflite model from assets
 * - construct an InterpreterApi successfully
 */
internal class ManagedTseProbe(
    private val context: Context
) {
    private var interpreter: InterpreterApi? = null

    suspend fun initialize(modelAssetName: String = "voice_filter_lite_int8.tflite"): Boolean {
        return try {
            TfLite.initialize(
                context,
                TfLiteInitializationOptions.builder().build()
            ).await()

            val modelBuffer = loadMappedAsset(modelAssetName)
            val options = InterpreterApi.Options()
                .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
            interpreter = InterpreterApi.create(modelBuffer, options)

            Log.i(TAG, "ManagedTseProbe initialized: model=$modelAssetName")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "ManagedTseProbe initialization failed for model=$modelAssetName", t)
            close()
            false
        }
    }

    fun isInitialized(): Boolean = interpreter != null

    fun close() {
        runCatching { interpreter?.close() }
        interpreter = null
    }

    private fun loadMappedAsset(assetName: String): MappedByteBuffer {
        context.assets.openFd(assetName).use { afd ->
            FileInputStream(afd.fileDescriptor).channel.use { channel ->
                return channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.startOffset,
                    afd.declaredLength
                )
            }
        }
    }
}
