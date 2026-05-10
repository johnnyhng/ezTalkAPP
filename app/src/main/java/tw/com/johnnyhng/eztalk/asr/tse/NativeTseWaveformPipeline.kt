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
        modelAssetName: String = "voice_filter_lite_int8.onnx",
        dvectorAssetName: String = "dvector.bin",
    ): Boolean {
        close()
        return try {
            val modelPath = copyAssetToCache(modelAssetName).absolutePath
            val dvectorPath = copyAssetToCache(dvectorAssetName).absolutePath
            initialized = nativeTse.init(modelPath, dvectorPath)
            if (initialized) {
                nativeTse.reset()
            }
            initialized
        } catch (t: Throwable) {
            Log.e(TAG, "NativeTseWaveformPipeline initialize failed", t)
            initialized = false
            false
        }
    }

    fun process(samples: FloatArray): FloatArray? {
        if (!initialized) return null
        if (samples.isEmpty()) return FloatArray(0)

        val output = ArrayList<Float>(samples.size + frameSize)
        var offset = 0
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
            output.addAll(processed.toList())
            offset += frameSize
        }

        return output.toFloatArray()
            .dropStreamingLatency()
            .copyOfRange(0, samples.size)
            .normalizedForNativeTse()
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

    private fun FloatArray.dropStreamingLatency(latencySamples: Int = 240): FloatArray {
        if (size <= latencySamples) return FloatArray(size)
        return FloatArray(size) { index ->
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

    private companion object {
        private const val TAG = "NativeTseWaveformPipeline"
    }
}
