package tw.com.johnnyhng.eztalk.asr.tse

import android.content.Context
import android.util.Log
import com.google.android.gms.tflite.acceleration.AccelerationConfig
import com.google.android.gms.tflite.acceleration.AccelerationService
import com.google.android.gms.tflite.acceleration.CpuAccelerationConfig
import com.google.android.gms.tflite.acceleration.CustomValidationConfig
import com.google.android.gms.tflite.acceleration.GpuAccelerationConfig
import com.google.android.gms.tflite.acceleration.Model
import com.google.android.gms.tflite.acceleration.ValidatedAccelerationConfigResult
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.gpu.support.TfLiteGpu
import com.google.android.gms.tflite.java.TfLite
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import tw.com.johnnyhng.eztalk.asr.TAG
import java.io.DataInputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
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

    internal data class LstmState(
        val h1: FloatArray = FloatArray(LSTM_DIM),
        val c1: FloatArray = FloatArray(LSTM_DIM),
        val h2: FloatArray = FloatArray(LSTM_DIM),
        val c2: FloatArray = FloatArray(LSTM_DIM)
    )

    internal data class SingleFrameResult(
        val mask: FloatArray,
        val nextState: LstmState
    )

    internal data class HardwareAccelerationStatus(
        val initialized: Boolean,
        val gpuAvailable: Boolean,
        val selectedAccelerator: String,
        val benchmarkPassed: Boolean,
        val hardwareAccelerated: Boolean
    )

    private var interpreter: InterpreterApi? = null
    private var lastHardwareAccelerationStatus: HardwareAccelerationStatus? = null

    suspend fun initialize(modelAssetName: String = "voice_filter_lite_int8.tflite"): Boolean {
        return try {
            val modelBuffer = loadMappedAsset(modelAssetName)
            val gpuAvailable = TfLiteGpu.isGpuDelegateAvailable(context).await()
            TfLite.initialize(
                context,
                TfLiteInitializationOptions.builder()
                    .setEnableGpuDelegateSupport(gpuAvailable)
                    .build()
            ).await()

            val validatedConfig = selectValidatedAccelerationConfig(modelAssetName, modelBuffer, gpuAvailable)
            val options = InterpreterApi.Options()
                .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
            if (validatedConfig != null) {
                validatedConfig.apply(options)
            }
            interpreter = InterpreterApi.create(modelBuffer, options)

            val acceleratorName = validatedConfig?.accelerationConfig()?.acceleratorName ?: "CPU fallback"
            val benchmarkPassed = validatedConfig?.benchmarkResult()?.hasPassedAccuracyCheck() ?: false
            lastHardwareAccelerationStatus = HardwareAccelerationStatus(
                initialized = true,
                gpuAvailable = gpuAvailable,
                selectedAccelerator = acceleratorName,
                benchmarkPassed = benchmarkPassed,
                hardwareAccelerated = acceleratorName != "CPU fallback"
            )
            Log.i(
                TAG,
                "ManagedTseProbe initialized: model=$modelAssetName gpuAvailable=$gpuAvailable accelerator=$acceleratorName benchmarkPassed=$benchmarkPassed"
            )
            true
        } catch (t: Throwable) {
            Log.e(TAG, "ManagedTseProbe initialization failed for model=$modelAssetName", t)
            lastHardwareAccelerationStatus = HardwareAccelerationStatus(
                initialized = false,
                gpuAvailable = false,
                selectedAccelerator = "none",
                benchmarkPassed = false,
                hardwareAccelerated = false
            )
            close()
            false
        }
    }

    fun isInitialized(): Boolean = interpreter != null

    fun hardwareAccelerationStatus(): HardwareAccelerationStatus {
        return lastHardwareAccelerationStatus ?: HardwareAccelerationStatus(
            initialized = false,
            gpuAvailable = false,
            selectedAccelerator = "unknown",
            benchmarkPassed = false,
            hardwareAccelerated = false
        )
    }

    fun runDummyInference(
        modelAssetName: String = "voice_filter_lite_int8.tflite",
        dvectorAssetName: String = "dvector.bin"
    ): Boolean {
        return try {
            runSingleFrame(
                cnnWindow = FloatArray(CNN_FRAMES * FREQ_BINS),
                embed = loadDvectorForTesting(dvectorAssetName),
                state = LstmState()
            )
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

    fun runSingleFrame(
        cnnWindow: FloatArray,
        embed: FloatArray,
        state: LstmState
    ): SingleFrameResult? {
        val localInterpreter = interpreter ?: return null
        require(cnnWindow.size == CNN_FRAMES * FREQ_BINS) {
            "cnnWindow must have ${CNN_FRAMES * FREQ_BINS} floats, got ${cnnWindow.size}"
        }
        require(embed.size == EMBED_DIM) {
            "embed must have $EMBED_DIM floats, got ${embed.size}"
        }
        require(state.h1.size == LSTM_DIM && state.c1.size == LSTM_DIM &&
            state.h2.size == LSTM_DIM && state.c2.size == LSTM_DIM) {
            "All LSTM state tensors must have $LSTM_DIM floats"
        }

        return try {
            val x = packNhwcWindow(cnnWindow)
            val embedIn = arrayOf(embed)
            val h1In = arrayOf(state.h1)
            val c1In = arrayOf(state.c1)
            val h2In = arrayOf(state.h2)
            val c2In = arrayOf(state.c2)

            val inputs = arrayOf(x, embedIn, h1In, c1In, h2In, c2In)
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

            val mask = FloatArray(FREQ_BINS) { idx -> maskOut[0][idx][0] }
            SingleFrameResult(
                mask = mask,
                nextState = LstmState(
                    h1 = h1Out[0].copyOf(),
                    c1 = c1Out[0].copyOf(),
                    h2 = h2Out[0].copyOf(),
                    c2 = c2Out[0].copyOf()
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "ManagedTseProbe runSingleFrame failed", t)
            null
        }
    }

    fun close() {
        runCatching { interpreter?.close() }
        interpreter = null
        lastHardwareAccelerationStatus = null
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

    private suspend fun selectValidatedAccelerationConfig(
        modelAssetName: String,
        modelBuffer: MappedByteBuffer,
        gpuAvailable: Boolean
    ): ValidatedAccelerationConfigResult? {
        val accelerationService = AccelerationService.create(context)
        val model = Model.Builder()
            .setModelNamespace("tw.com.johnnyhng.eztalk.asr.tse")
            .setModelId(modelAssetName.removeSuffix(".tflite"))
            .setModelLocation(Model.ModelLocation.fromByteBuffer(modelBuffer))
            .build()

        val validationConfig = CustomValidationConfig.Builder()
            .setBatchSize(1)
            .setGoldenInputs(
                zeroCnnWindowInput(),
                zeroEmbedInput(),
                zeroStateInput(),
                zeroStateInput(),
                zeroStateInput(),
                zeroStateInput()
            )
            .setAccuracyValidator(CustomValidationConfig.SKIP_VALIDATION)
            .build()

        val configs = buildList<AccelerationConfig> {
            if (gpuAvailable) {
                add(
                    GpuAccelerationConfig.Builder()
                        .setEnableQuantizedInference(true)
                        .build()
                )
            }
            add(
                CpuAccelerationConfig.Builder()
                    .setNumThreads(1)
                    .build()
            )
        }

        val result = accelerationService.selectBestConfig(model, configs, validationConfig).await()
        if (result == null) {
            Log.i(TAG, "ManagedTseProbe acceleration validation returned no valid config")
            return null
        }

        if (!result.isValid()) {
            Log.i(
                TAG,
                "ManagedTseProbe acceleration validation invalid: accelerator=${result.accelerationConfig().acceleratorName} error=${result.benchmarkError()}"
            )
            return null
        }

        Log.i(
            TAG,
            "ManagedTseProbe acceleration validation selected: accelerator=${result.accelerationConfig().acceleratorName} inferenceMicros=${result.benchmarkResult().inferenceTimeMicros()}"
        )
        return result
    }

    private fun zeroCnnWindowInput(): FloatBuffer = allocateFloatBuffer(CNN_FRAMES * FREQ_BINS)

    private fun zeroEmbedInput(): FloatBuffer = allocateFloatBuffer(EMBED_DIM)

    private fun zeroStateInput(): FloatBuffer = allocateFloatBuffer(LSTM_DIM)

    private fun allocateFloatBuffer(size: Int): FloatBuffer {
        return ByteBuffer.allocateDirect(size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { position(0) }
    }

    internal fun loadDvectorForTesting(assetName: String): FloatArray {
        context.assets.open(assetName).use { input ->
            DataInputStream(input).use { data ->
                val bytes = ByteArray(EMBED_DIM * Float.SIZE_BYTES)
                data.readFully(bytes)
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                return FloatArray(EMBED_DIM) { buffer.float }
            }
        }
    }

    private fun packNhwcWindow(cnnWindow: FloatArray): Array<Array<Array<FloatArray>>> {
        return Array(1) { batch ->
            Array(CNN_FRAMES) { frame ->
                Array(FREQ_BINS) { bin ->
                    FloatArray(1) {
                        cnnWindow[batch * CNN_FRAMES * FREQ_BINS + frame * FREQ_BINS + bin]
                    }
                }
            }
        }
    }
}
