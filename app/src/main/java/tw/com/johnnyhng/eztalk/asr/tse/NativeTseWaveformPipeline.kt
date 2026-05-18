package tw.com.johnnyhng.eztalk.asr.tse

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Offline wrapper around the native ONNX VoiceFilter Lite JNI path.
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

    fun initialize(
        modelAssetName: String = "transformer_energy_16d_1L_int8.onnx",
        dvectorAssetName: String = "dvector.bin",
        accelerationMode: Int = NativeTSE.ACCELERATION_CPU,
    ): Boolean {
        close()
        return try {
            val modelPath = copyAssetToCache(modelAssetName).absolutePath
            val dvectorPath = copyAssetToCache(dvectorAssetName).absolutePath
            val accelerationName = NativeTSE.accelerationModeName(accelerationMode)
            initialized = if (accelerationMode == NativeTSE.ACCELERATION_CPU) {
                nativeTse.init(modelPath, dvectorPath)
            } else {
                nativeTse.initWithAcceleration(modelPath, dvectorPath, accelerationMode)
            }
            if (initialized) {
                nativeTse.reset()
                Log.i(TAG, "Native TSE initialized. acceleration=$accelerationName")
            } else {
                Log.w(TAG, "Native TSE initialize returned false. acceleration=$accelerationName")
            }
            initialized
        } catch (t: Throwable) {
            Log.e(TAG, "NativeTseWaveformPipeline initialize failed. acceleration=${NativeTSE.accelerationModeName(accelerationMode)}", t)
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
            .normalizedForNativeTse()
            .zeroIstftBoundaryArtifacts()
            
        Log.i(TAG, "NativeTseWaveformPipeline process complete: input=${samples.size}, output=${finalResult.size}")
        return finalResult
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

    private fun FloatArray.dropStreamingLatency(targetSize: Int, latencySamples: Int = 240): FloatArray {
        if (size <= latencySamples) return FloatArray(targetSize)
        return FloatArray(targetSize) { index ->
            val source = index + latencySamples
            if (source < size) this[source] else 0f
        }
    }

    private fun FloatArray.normalizedForNativeTse(): FloatArray {
        var maxAbs = 0f
        for (sample in this) {
            val abs = kotlin.math.abs(sample)
            if (abs > maxAbs) maxAbs = abs
        }
        if (maxAbs <= 1e-8f) return this
        val scale = 0.9f / maxAbs
        return FloatArray(size) { index -> this[index] * scale }
    }

    private fun FloatArray.zeroIstftBoundaryArtifacts(edgeSamples: Int = 400): FloatArray {
        if (isEmpty()) return this
        val muted = copyOf()
        val edge = minOf(edgeSamples, muted.size / 4)
        if (edge <= 0) return muted
        for (index in 0 until edge) {
            muted[index] = 0f
            muted[muted.lastIndex - index] = 0f
        }
        Log.i(TAG, "NativeTseWaveformPipeline zeroed boundary artifacts: edgeSamples=$edge totalSamples=${muted.size}")
        return muted
    }

    private companion object {
        private const val TAG = "NativeTseWaveformPipeline"
    }
}
