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

    private enum class InputLayout {
        NHWC,
        NCHW
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
    private var inputLayout: InputLayout = InputLayout.NHWC

    suspend fun initialize(modelAssetName: String = "voice_filter_lite.tflite"): Boolean {
        return try {
            val modelBuffer = loadMappedAsset(modelAssetName)
            val gpuAvailable = TfLiteGpu.isGpuDelegateAvailable(context).await()
            TfLite.initialize(
                context,
                TfLiteInitializationOptions.builder()
                    .setEnableGpuDelegateSupport(gpuAvailable)
                    .build()
            ).await()

            val validatedConfig = selectValidatedAccelerationConfig(
                modelAssetName = modelAssetName,
                modelBuffer = modelBuffer,
                gpuAvailable = gpuAvailable
            )
            val options = InterpreterApi.Options()
                .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
            if (validatedConfig != null) {
                validatedConfig.apply(options)
            }
            interpreter = InterpreterApi.create(modelBuffer, options)
            configureStaticTensorShapes(interpreter!!)

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
        modelAssetName: String = "voice_filter_lite.tflite",
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
            val x = packWindowArray(cnnWindow)
            val embedIn = arrayOf(embed)
            val hIn = packStackedStateArray(state.h1, state.h2)
            val cIn = packStackedStateArray(state.c1, state.c2)
            val inputs = arrayOf(x, embedIn, hIn, cIn)
            val maskOut = Array(1) { Array(FREQ_BINS) { FloatArray(1) } }
            val stackedHOut = Array(2) { Array(1) { FloatArray(LSTM_DIM) } }
            val stackedCOut = Array(2) { Array(1) { FloatArray(LSTM_DIM) } }
            val outputs = mutableMapOf<Int, Any>(
                0 to maskOut,
                1 to stackedHOut,
                2 to stackedCOut
            )

            localInterpreter.runForMultipleInputsOutputs(inputs, outputs)

            SingleFrameResult(
                mask = FloatArray(FREQ_BINS) { index -> maskOut[0][index][0] },
                nextState = readStackedState(stackedHOut, stackedCOut)
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
        inputLayout = InputLayout.NHWC
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
                zeroStackedStateInput(),
                zeroStackedStateInput()
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

    private fun configureStaticTensorShapes(interpreter: InterpreterApi) {
        require(interpreter.getInputTensorCount() == 4) {
            "ManagedTseProbe expects stacked-state model with 4 inputs, got ${interpreter.getInputTensorCount()}"
        }
        require(interpreter.getOutputTensorCount() == 3) {
            "ManagedTseProbe expects stacked-state model with 3 outputs, got ${interpreter.getOutputTensorCount()}"
        }

        maybeResizeInput(interpreter, 0, intArrayOf(1, CNN_FRAMES, FREQ_BINS, 1))
        maybeResizeInput(interpreter, 1, intArrayOf(1, EMBED_DIM))
        maybeResizeInput(interpreter, 2, intArrayOf(2, 1, LSTM_DIM))
        maybeResizeInput(interpreter, 3, intArrayOf(2, 1, LSTM_DIM))
        interpreter.allocateTensors()

        val input0Shape = interpreter.getInputTensor(0).shape()
        inputLayout = when {
            input0Shape.contentEquals(intArrayOf(1, CNN_FRAMES, FREQ_BINS, 1)) -> InputLayout.NHWC
            input0Shape.contentEquals(intArrayOf(1, 1, CNN_FRAMES, FREQ_BINS)) -> InputLayout.NCHW
            else -> error("Unsupported input tensor 0 shape: ${input0Shape.contentToString()}")
        }
        val maskOutputElements = interpreter.getOutputTensor(0).numElements()
        require(maskOutputElements == FREQ_BINS) {
            "ManagedTseProbe expects mask output with $FREQ_BINS elements, got $maskOutputElements"
        }

        val inputSummary = buildString {
            append("inputs=")
            append(
                (0 until interpreter.getInputTensorCount()).joinToString(separator = "; ") { index ->
                    val tensor = interpreter.getInputTensor(index)
                    "[$index name=${tensor.name()} shape=${tensor.shape().contentToString()} sig=${tensor.shapeSignature().contentToString()}]"
                }
            )
        }
        val outputSummary = buildString {
            append("outputs=")
            append(
                (0 until interpreter.getOutputTensorCount()).joinToString(separator = "; ") { index ->
                    val tensor = interpreter.getOutputTensor(index)
                    "[$index name=${tensor.name()} shape=${tensor.shape().contentToString()} sig=${tensor.shapeSignature().contentToString()}]"
                }
            )
        }
        Log.i(TAG, "ManagedTseProbe tensor allocation complete: inputLayout=$inputLayout stateLayout=STACKED_LAYERS $inputSummary $outputSummary")
    }

    private fun maybeResizeInput(interpreter: InterpreterApi, index: Int, preferredShape: IntArray) {
        val tensor = interpreter.getInputTensor(index)
        val signature = tensor.shapeSignature()
        val current = tensor.shape()
        if (signature.contentEquals(current)) {
            return
        }
        val target = current.copyOf()
        var changed = false
        for (dim in target.indices) {
            if (signature[dim] == -1) {
                target[dim] = preferredShape[dim]
                changed = true
            }
        }
        if (changed) {
            interpreter.resizeInput(index, target, true)
        }
    }

    private fun zeroCnnWindowInput(): FloatBuffer = allocateFloatBuffer(CNN_FRAMES * FREQ_BINS)

    private fun zeroEmbedInput(): FloatBuffer = allocateFloatBuffer(EMBED_DIM)

    private fun zeroStackedStateInput(): FloatBuffer = allocateFloatBuffer(2 * LSTM_DIM)

    private fun allocateFloatBuffer(size: Int): FloatBuffer {
        return ByteBuffer.allocateDirect(size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { position(0) }
    }

    private fun readStackedState(
        h: Array<Array<FloatArray>>,
        c: Array<Array<FloatArray>>
    ): LstmState {
        return LstmState(
            h1 = h[0][0].copyOf(),
            c1 = c[0][0].copyOf(),
            h2 = h[1][0].copyOf(),
            c2 = c[1][0].copyOf()
        )
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

    private fun packStackedStateArray(
        layer0: FloatArray,
        layer1: FloatArray
    ): Array<Array<FloatArray>> {
        return Array(2) { layer ->
            Array(1) {
                when (layer) {
                    0 -> layer0.copyOf()
                    else -> layer1.copyOf()
                }
            }
        }
    }

    private fun packWindowArray(cnnWindow: FloatArray): Any {
        return when (inputLayout) {
            InputLayout.NHWC -> {
                Array(1) {
                    Array(CNN_FRAMES) { frame ->
                        Array(FREQ_BINS) { bin ->
                            FloatArray(1) {
                                cnnWindow[frame * FREQ_BINS + bin]
                            }
                        }
                    }
                }
            }
            InputLayout.NCHW -> {
                Array(1) {
                    Array(1) {
                        Array(CNN_FRAMES) { frame ->
                            FloatArray(FREQ_BINS) { bin ->
                                cnnWindow[frame * FREQ_BINS + bin]
                            }
                        }
                    }
                }
            }
        }
    }
}
