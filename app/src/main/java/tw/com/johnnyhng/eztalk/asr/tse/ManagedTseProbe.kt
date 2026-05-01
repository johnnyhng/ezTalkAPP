package tw.com.johnnyhng.eztalk.asr.tse

import android.content.Context
import android.util.Log
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.java.TfLite
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import tw.com.johnnyhng.eztalk.asr.TAG
import java.io.DataInputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    companion object {
        private const val CNN_FRAMES = 32
        private const val FREQ_BINS = 257
        private const val EMBED_DIM = 192
        private const val LSTM_DIM = 512
    }

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

    fun runDummyInference(
        modelAssetName: String = "voice_filter_lite_int8.tflite",
        dvectorAssetName: String = "dvector.bin"
    ): Boolean {
        val localInterpreter = interpreter ?: return false
        return try {
            val x = Array(1) { Array(CNN_FRAMES) { Array(FREQ_BINS) { FloatArray(1) } } }
            val embed = arrayOf(loadDvector(dvectorAssetName))
            val h1In = arrayOf(FloatArray(LSTM_DIM))
            val c1In = arrayOf(FloatArray(LSTM_DIM))
            val h2In = arrayOf(FloatArray(LSTM_DIM))
            val c2In = arrayOf(FloatArray(LSTM_DIM))

            val inputs = arrayOf(x, embed, h1In, c1In, h2In, c2In)
            val maskOut = Array(1) { Array(FREQ_BINS) { FloatArray(1) } }
            val h1Out = Array(1) { FloatArray(LSTM_DIM) }
            val c1Out = Array(1) { FloatArray(LSTM_DIM) }
            val h2Out = Array(1) { FloatArray(LSTM_DIM) }
            val c2Out = Array(1) { FloatArray(LSTM_DIM) }
            val outputs = mutableMapOf<Int, Any>(
                0 to maskOut,
                1 to h1Out,
                2 to c1Out,
                3 to h2Out,
                4 to c2Out
            )

            localInterpreter.runForMultipleInputsOutputs(inputs, outputs)
            Log.i(
                TAG,
                "ManagedTseProbe dummy inference succeeded: model=$modelAssetName maskShape=[1,$FREQ_BINS,1] stateDim=$LSTM_DIM"
            )
            true
        } catch (t: Throwable) {
            Log.e(TAG, "ManagedTseProbe dummy inference failed for model=$modelAssetName", t)
            false
        }
    }

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

    private fun loadDvector(assetName: String): FloatArray {
        context.assets.open(assetName).use { input ->
            DataInputStream(input).use { data ->
                val bytes = ByteArray(EMBED_DIM * Float.SIZE_BYTES)
                data.readFully(bytes)
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                return FloatArray(EMBED_DIM) { buffer.float }
            }
        }
    }
}
