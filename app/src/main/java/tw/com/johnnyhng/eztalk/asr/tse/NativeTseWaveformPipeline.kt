package tw.com.johnnyhng.eztalk.asr.tse

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Offline wrapper around the native ONNX SOUL Filter (EAT Engine) JNI path.
 *
 * The native side owns STFT, CNN/LSTM state, masking, and overlap-add. This
 * class handles Android asset extraction and hop framing.
 */
internal class NativeTseWaveformPipeline(
    private val context: Context,
    private val nativeTse: NativeTSE = NativeTSE(),
    private val frameSize: Int = 160,
) {
    private var initialized = false

    /**
     * Initialize from absolute filesystem paths.
     */
    fun initialize(
        modelPath: String,
        dvectorPath: String,
        accelerationMode: Int = NativeTSE.ACCELERATION_CPU,
    ): Boolean = initializeMulti(
        modelPath = modelPath,
        dvectorPaths = arrayOf(dvectorPath),
        accelerationMode = accelerationMode,
    )

    /**
     * Initialize from absolute filesystem paths for one or more target speakers.
     */
    fun initializeMulti(
        modelPath: String,
        dvectorPaths: Array<String>,
        accelerationMode: Int = NativeTSE.ACCELERATION_CPU,
    ): Boolean {
        close()
        return try {
            val modelFile = File(modelPath)
            val missingDvector = dvectorPaths.firstOrNull { !File(it).exists() }
            if (!modelFile.exists() || missingDvector != null) {
                Log.e(TAG, "TSE model or dvector path does not exist. modelPath=$modelPath, missingDvector=$missingDvector")
                return false
            }
            val accelerationName = NativeTSE.accelerationModeName(accelerationMode)
            initialized = if (accelerationMode == NativeTSE.ACCELERATION_CPU) {
                nativeTse.initMulti(modelPath, dvectorPaths)
            } else {
                nativeTse.initWithAccelerationMulti(modelPath, dvectorPaths, accelerationMode)
            }
            if (initialized) {
                nativeTse.reset()
                Log.i(TAG, "Native SOUL Filter initialized. modelPath=$modelPath, numSpeakers=${dvectorPaths.size}, acceleration=$accelerationName")
            } else {
                Log.w(TAG, "Native SOUL Filter initialize returned false. acceleration=$accelerationName")
            }
            initialized
        } catch (t: Throwable) {
            Log.e(TAG, "NativeTseWaveformPipeline initialize failed. acceleration=${NativeTSE.accelerationModeName(accelerationMode)}", t)
            initialized = false
            false
        }
    }

    fun initialize(
        accelerationMode: Int = NativeTSE.ACCELERATION_CPU,
    ): Boolean {
        return try {
            val modelPath = copyFirstExistingAssetToCache(
                "soul_filter_eat_int8.onnx",
                "transformer_energy_64d_1L_int8.onnx",
            ).absolutePath
            val dvectorPath = copyAssetToCache("dvector.bin").absolutePath
            initialize(modelPath, dvectorPath, accelerationMode)
        } catch (t: Throwable) {
            Log.e(TAG, "NativeTseWaveformPipeline initialize assets failed", t)
            initialized = false
            false
        }
    }

    fun process(samples: FloatArray): FloatArray? {
        if (!initialized) return null
        if (samples.isEmpty()) return FloatArray(0)

        val hopCount = (samples.size + frameSize - 1) / frameSize
        val output = FloatArray(hopCount * frameSize)
        var offset = 0
        var outputOffset = 0
        while (offset < samples.size) {
            val hop = FloatArray(frameSize)
            val remaining = samples.size - offset
            samples.copyInto(
                destination = hop,
                destinationOffset = 0,
                startIndex = offset,
                endIndex = offset + minOf(remaining, frameSize),
            )
            val processed = nativeTse.processFrame(hop) ?: return null
            processed.copyInto(output, destinationOffset = outputOffset)
            offset += frameSize
            outputOffset += frameSize
        }

        val finalResult = output
            .dropStreamingLatency(targetSize = samples.size)
            .normalizedForNativeSoulFilter()
            
        Log.i(TAG, "NativeTseWaveformPipeline process complete: input=${samples.size}, output=${finalResult.size}")
        return finalResult
    }

    fun processMulti(samples: FloatArray): List<FloatArray>? {
        if (!initialized) return null
        if (samples.isEmpty()) return emptyList()

        val numSpeakers = nativeTse.getNumSpeakers()
        if (numSpeakers <= 0) {
            Log.w(TAG, "NativeTseWaveformPipeline processMulti failed: native speaker count is $numSpeakers")
            return null
        }

        val hopCount = (samples.size + frameSize - 1) / frameSize
        val speakerOutputs = List(numSpeakers) { FloatArray(hopCount * frameSize) }
        var offset = 0
        var outputOffset = 0
        while (offset < samples.size) {
            val hop = FloatArray(frameSize)
            val remaining = samples.size - offset
            samples.copyInto(
                destination = hop,
                destinationOffset = 0,
                startIndex = offset,
                endIndex = offset + minOf(remaining, frameSize),
            )
            val processedFlat = nativeTse.processFrameMulti(hop) ?: return null
            if (processedFlat.size < numSpeakers * frameSize) {
                Log.w(TAG, "NativeTseWaveformPipeline processMulti failed: outputSize=${processedFlat.size}, expected=${numSpeakers * frameSize}")
                return null
            }
            for (speakerIndex in 0 until numSpeakers) {
                processedFlat.copyInto(
                    destination = speakerOutputs[speakerIndex],
                    destinationOffset = outputOffset,
                    startIndex = speakerIndex * frameSize,
                    endIndex = (speakerIndex + 1) * frameSize,
                )
            }
            offset += frameSize
            outputOffset += frameSize
        }

        return speakerOutputs.map { output ->
            output
                .dropStreamingLatency(targetSize = samples.size)
                .normalizedForNativeSoulFilter()
        }
    }

    fun close() {
        if (initialized) {
            runCatching { nativeTse.release() }
        }
        initialized = false
    }

    private fun copyAssetToCache(assetName: String): File {
        val targetDir = File(context.cacheDir, "native-tse").apply { mkdirs() }
        val target = File(targetDir, assetName)
        context.assets.open(assetName).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return target
    }

    private fun copyFirstExistingAssetToCache(vararg assetNames: String): File {
        var lastFailure: Throwable? = null
        for (assetName in assetNames) {
            try {
                return copyAssetToCache(assetName)
            } catch (t: Throwable) {
                lastFailure = t
            }
        }
        throw IllegalStateException("None of the requested TSE assets exist: ${assetNames.joinToString()}", lastFailure)
    }

    private fun FloatArray.dropStreamingLatency(targetSize: Int, latencySamples: Int = 240): FloatArray {
        if (size <= latencySamples) return FloatArray(targetSize)
        return FloatArray(targetSize) { index ->
            val source = index + latencySamples
            if (source < size) this[source] else 0f
        }
    }

    private fun FloatArray.normalizedForNativeSoulFilter(): FloatArray {
        var maxAbs = 0f
        for (sample in this) {
            val abs = kotlin.math.abs(sample)
            if (abs > maxAbs) maxAbs = abs
        }
        if (maxAbs <= 1e-8f) return this
        val scale = 0.9f / maxAbs
        return FloatArray(size) { index -> this[index] * scale }
    }

    private companion object {
        private const val TAG = "NativeTseWaveformPipeline"
    }
}
