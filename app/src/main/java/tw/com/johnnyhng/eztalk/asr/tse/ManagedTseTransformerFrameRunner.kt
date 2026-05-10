package tw.com.johnnyhng.eztalk.asr.tse

import android.content.Context
import android.util.Log
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.gpu.support.TfLiteGpu
import com.google.android.gms.tflite.java.TfLite
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import org.tensorflow.lite.gpu.GpuDelegateFactory
import org.tensorflow.lite.gpu.GpuDelegateFactory.Options.GpuBackend
import tw.com.johnnyhng.eztalk.asr.TAG
import java.io.DataInputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import kotlin.math.pow

/**
 * Streaming runner for the 64D managed TSE exports.
 *
 * Supported contracts:
 * - TCN:         spec input + embed input -> mask output
 * - Transformer: spec input + embed input + pos input -> mask output
 *
 * Shared tensor shapes:
 * - spec input:  [1, 80, 257, 1] float32
 * - embed input: [1, 64] float32
 * - mask output: [1, 80, 257, 1] float32 for fp16/fp32, int8 for int8 test artifact
 */
internal class ManagedTseTransformerFrameRunner(
    private val context: Context
) : ManagedTseMaskFrameRunner {
    private enum class ModelContract {
        TCN_2_INPUT,
        TRANSFORMER_3_INPUT
    }

    private enum class OutputType {
        FLOAT32,
        INT8
    }

    private data class DenoiseConfig(
        val maskPower: Float = 1.0f,
        val smoothing: Float = 0.7f,
        val softGate: Float = 0.15f,
        val softGateGain: Float = 0.2f,
        val hardGate: Float = 0.08f
    )

    companion object {
        private const val CONTEXT_FRAMES = 80
        private const val FREQ_BINS = 257
        private const val EMBED_DIM = 64
        private const val POS_DIM = 81
        private const val OUTPUT_SCALE = 0.00390625f
        private const val OUTPUT_ZERO_POINT = -128
    }

    private val config = DenoiseConfig()
    private val specBuffer = Array(1) { Array(CONTEXT_FRAMES) { Array(FREQ_BINS) { FloatArray(1) } } }
    private val posInput = Array(1) { FloatArray(POS_DIM) { it.toFloat() } }
    private var embedInput: Array<FloatArray>? = null
    private var previousMask: FloatArray? = null
    private var interpreter: InterpreterApi? = null
    private var acceleratorName: String = "none"
    private var modelContract: ModelContract = ModelContract.TRANSFORMER_3_INPUT
    private var outputType: OutputType = OutputType.FLOAT32
    private var lastGpuFailureSummary: String? = null
    private val inferenceDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ManagedTse64d")
    }.asCoroutineDispatcher()

    override suspend fun initialize(
        modelAssetName: String,
        dvectorAssetName: String
    ): Boolean {
        return try {
            val modelBuffer = loadMappedAsset(modelAssetName)
            val gpuAvailable = TfLiteGpu.isGpuDelegateAvailable(context).await()
            TfLite.initialize(
                context,
                TfLiteInitializationOptions.builder()
                    .setEnableGpuDelegateSupport(gpuAvailable)
                    .build()
            ).await()

            withContext(inferenceDispatcher) {
                embedInput = arrayOf(loadDvector64(dvectorAssetName))
                resetBuffers()
                interpreter = createInterpreter(
                    modelAssetName = modelAssetName,
                    modelBuffer = modelBuffer,
                    gpuAvailable = gpuAvailable
                )
                configureStaticTensorShapes(interpreter!!)
            }
            Log.i(
                TAG,
                "ManagedTseTransformerFrameRunner initialized: model=$modelAssetName dvector=$dvectorAssetName contract=$modelContract gpuAvailable=$gpuAvailable accelerator=$acceleratorName gpuFailure=${lastGpuFailureSummary ?: "none"}"
            )
            true
        } catch (t: Throwable) {
            Log.e(TAG, "ManagedTseTransformerFrameRunner initialization failed for model=$modelAssetName", t)
            close()
            false
        }
    }

    override fun processMagnitudeFrame(magFrame: FloatArray): FloatArray? {
        return runBlocking(inferenceDispatcher) {
            processMagnitudeFrameOnInferenceThread(magFrame)
        }
    }

    private fun processMagnitudeFrameOnInferenceThread(magFrame: FloatArray): FloatArray? {
        val localInterpreter = interpreter ?: return null
        val localEmbed = embedInput ?: return null
        require(magFrame.size == FREQ_BINS) {
            "magFrame must have $FREQ_BINS samples, got ${magFrame.size}"
        }

        shiftSpecBuffer()
        for (bin in 0 until FREQ_BINS) {
            specBuffer[0][CONTEXT_FRAMES - 1][bin][0] = magFrame[bin]
        }

        return try {
            val rawMask = when (outputType) {
                OutputType.FLOAT32 -> runFloatOutputInference(localInterpreter, localEmbed)
                OutputType.INT8 -> runInt8OutputInference(localInterpreter, localEmbed)
            }
            postprocessMask(rawMask).also { previousMask = it }
        } catch (t: Throwable) {
            Log.e(TAG, "ManagedTseTransformerFrameRunner processMagnitudeFrame failed", t)
            null
        }
    }

    override fun reset() {
        runBlocking(inferenceDispatcher) {
            resetBuffers()
        }
    }

    override fun close() {
        runCatching {
            runBlocking(inferenceDispatcher) {
                runCatching { interpreter?.close() }
            }
        }
        interpreter = null
        embedInput = null
        acceleratorName = "none"
        modelContract = ModelContract.TRANSFORMER_3_INPUT
        outputType = OutputType.FLOAT32
        lastGpuFailureSummary = null
        runCatching {
            runBlocking(inferenceDispatcher) {
                resetBuffers()
            }
        }
    }

    private fun createInterpreter(
        modelAssetName: String,
        modelBuffer: MappedByteBuffer,
        gpuAvailable: Boolean
    ): InterpreterApi {
        if (gpuAvailable) {
            lastGpuFailureSummary = null
            val gpuBackends = listOf<GpuBackend?>(null, GpuBackend.OPENCL, GpuBackend.OPENGL)
            val failures = mutableListOf<String>()
            for (backend in gpuBackends) {
                try {
                    val gpuInterpreter = createGpuInterpreter(modelBuffer, backend)
                    val backendName = backend?.name ?: "DEFAULT"
                    acceleratorName = "GPU delegate ($backendName)"
                    lastGpuFailureSummary = null
                    Log.i(TAG, "ManagedTseTransformerFrameRunner GPU interpreter created: model=$modelAssetName backend=$backendName")
                    return gpuInterpreter
                } catch (t: Throwable) {
                    val backendName = backend?.name ?: "DEFAULT"
                    val failure = "$backendName=${t::class.java.simpleName}: ${t.message ?: "no message"}"
                    failures.add(failure)
                    Log.w(TAG, "ManagedTseTransformerFrameRunner GPU $backendName interpreter failed: model=$modelAssetName", t)
                }
            }
            lastGpuFailureSummary = failures.joinToString(separator = " | ")
            Log.w(TAG, "ManagedTseTransformerFrameRunner all GPU backends failed: model=$modelAssetName failures=$lastGpuFailureSummary")
            error("GPU delegate required but unavailable: $lastGpuFailureSummary")
        }

        acceleratorName = "GPU unavailable"
        lastGpuFailureSummary = "TfLiteGpu.isGpuDelegateAvailable=false"
        error("GPU delegate required but unavailable: $lastGpuFailureSummary")
    }

    private fun createGpuInterpreter(
        modelBuffer: MappedByteBuffer,
        backend: GpuBackend?
    ): InterpreterApi {
        val gpuOptions = GpuDelegateFactory.Options()
            .setPrecisionLossAllowed(true)
            .setQuantizedModelsAllowed(true)
            .setInferencePreference(GpuDelegateFactory.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
        if (backend != null) {
            gpuOptions.setForceBackend(backend)
        }
        val options = InterpreterApi.Options()
            .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
            .addDelegateFactory(GpuDelegateFactory(gpuOptions))
        return InterpreterApi.create(modelBuffer, options)
    }

    private fun configureStaticTensorShapes(interpreter: InterpreterApi) {
        val inputTensorCount = interpreter.getInputTensorCount()
        modelContract = when (inputTensorCount) {
            2 -> ModelContract.TCN_2_INPUT
            3 -> ModelContract.TRANSFORMER_3_INPUT
            else -> error("64D TSE expects 2-input TCN or 3-input Transformer model, got $inputTensorCount inputs")
        }
        require(interpreter.getOutputTensorCount() == 1) {
            "64D TSE expects 1 output, got ${interpreter.getOutputTensorCount()}"
        }

        maybeResizeInput(interpreter, 0, intArrayOf(1, CONTEXT_FRAMES, FREQ_BINS, 1))
        maybeResizeInput(interpreter, 1, intArrayOf(1, EMBED_DIM))
        if (modelContract == ModelContract.TRANSFORMER_3_INPUT) {
            maybeResizeInput(interpreter, 2, intArrayOf(1, POS_DIM))
        }
        interpreter.allocateTensors()

        require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, CONTEXT_FRAMES, FREQ_BINS, 1))) {
            "Unexpected spec input shape: ${interpreter.getInputTensor(0).shape().contentToString()}"
        }
        require(interpreter.getInputTensor(1).numElements() == EMBED_DIM) {
            "Unexpected dvector input elements: ${interpreter.getInputTensor(1).numElements()}"
        }
        if (modelContract == ModelContract.TRANSFORMER_3_INPUT) {
            require(interpreter.getInputTensor(2).numElements() == POS_DIM) {
                "Unexpected position input elements: ${interpreter.getInputTensor(2).numElements()}"
            }
        }
        require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, CONTEXT_FRAMES, FREQ_BINS, 1))) {
            "Unexpected mask output shape: ${interpreter.getOutputTensor(0).shape().contentToString()}"
        }
        outputType = when (interpreter.getOutputTensor(0).dataType()) {
            DataType.FLOAT32 -> OutputType.FLOAT32
            DataType.INT8 -> OutputType.INT8
            else -> error("Unsupported mask output type: ${interpreter.getOutputTensor(0).dataType()}")
        }

        val inputSummary = (0 until interpreter.getInputTensorCount()).joinToString(separator = "; ") { index ->
            val tensor = interpreter.getInputTensor(index)
            "[$index name=${tensor.name()} type=${tensor.dataType()} shape=${tensor.shape().contentToString()} sig=${tensor.shapeSignature().contentToString()}]"
        }
        val outputSummary = (0 until interpreter.getOutputTensorCount()).joinToString(separator = "; ") { index ->
            val tensor = interpreter.getOutputTensor(index)
            "[$index name=${tensor.name()} type=${tensor.dataType()} shape=${tensor.shape().contentToString()} sig=${tensor.shapeSignature().contentToString()} quant=${tensor.quantizationParams().scale}/${tensor.quantizationParams().zeroPoint}]"
        }
        Log.i(TAG, "ManagedTseTransformerFrameRunner tensor allocation complete: contract=$modelContract outputType=$outputType inputs=$inputSummary outputs=$outputSummary")
    }

    private fun runFloatOutputInference(
        interpreter: InterpreterApi,
        localEmbed: Array<FloatArray>
    ): FloatArray {
        val maskOutput = Array(1) { Array(CONTEXT_FRAMES) { Array(FREQ_BINS) { FloatArray(1) } } }
        interpreter.runForMultipleInputsOutputs(
            modelInputs(localEmbed),
            mutableMapOf<Int, Any>(0 to maskOutput)
        )
        return FloatArray(FREQ_BINS) { bin ->
            maskOutput[0][CONTEXT_FRAMES - 1][bin][0].coerceIn(0f, 1f)
        }
    }

    private fun runInt8OutputInference(
        interpreter: InterpreterApi,
        localEmbed: Array<FloatArray>
    ): FloatArray {
        val maskOutput = Array(1) { Array(CONTEXT_FRAMES) { Array(FREQ_BINS) { ByteArray(1) } } }
        interpreter.runForMultipleInputsOutputs(
            modelInputs(localEmbed),
            mutableMapOf<Int, Any>(0 to maskOutput)
        )
        return FloatArray(FREQ_BINS) { bin ->
            ((maskOutput[0][CONTEXT_FRAMES - 1][bin][0].toInt() - OUTPUT_ZERO_POINT) * OUTPUT_SCALE)
                .coerceIn(0f, 1f)
        }
    }

    private fun modelInputs(localEmbed: Array<FloatArray>): Array<Any> {
        return when (modelContract) {
            ModelContract.TCN_2_INPUT -> arrayOf(specBuffer, localEmbed)
            ModelContract.TRANSFORMER_3_INPUT -> arrayOf(specBuffer, localEmbed, posInput)
        }
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

    private fun postprocessMask(rawMask: FloatArray): FloatArray {
        val previous = previousMask
        return FloatArray(rawMask.size) { index ->
            var value = rawMask[index].coerceIn(0f, 1f)
            if (config.maskPower != 1.0f) {
                value = value.toDouble().pow(config.maskPower.toDouble()).toFloat()
            }
            if (previous != null && config.smoothing > 0f) {
                value = config.smoothing * previous[index] + (1f - config.smoothing) * value
            }
            when {
                value < config.hardGate -> 0f
                value < config.softGate -> value * config.softGateGain
                else -> value
            }
        }
    }

    private fun shiftSpecBuffer() {
        for (frame in 0 until CONTEXT_FRAMES - 1) {
            for (bin in 0 until FREQ_BINS) {
                specBuffer[0][frame][bin][0] = specBuffer[0][frame + 1][bin][0]
            }
        }
    }

    private fun resetBuffers() {
        for (frame in 0 until CONTEXT_FRAMES) {
            for (bin in 0 until FREQ_BINS) {
                specBuffer[0][frame][bin][0] = 0f
            }
        }
        previousMask = null
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

    private fun loadDvector64(assetName: String): FloatArray {
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
