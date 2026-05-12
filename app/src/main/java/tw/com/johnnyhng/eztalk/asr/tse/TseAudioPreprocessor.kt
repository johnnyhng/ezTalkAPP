package tw.com.johnnyhng.eztalk.asr.tse

import android.content.Context
import android.util.Log
import java.io.File

internal data class TseChunkOutput(
    val rawAligned: FloatArray,
    val processed: FloatArray
)

/**
 * Realtime TSE preprocessor using NativeTSE.
 *
 * It takes raw audio chunks, frames them to 160 samples (10ms),
 * processes them through the native TSE engine, and returns
 * aligned raw and processed frames by accounting for the 15ms (240 samples)
 * inherent streaming latency of the model.
 */
internal class TseAudioPreprocessor(
    private val frameSize: Int = 160,
    private val latencySamples: Int = 240
) {
    private val TAG = "TseAudioPreprocessor"
    private val pendingRaw = ArrayList<Float>()
    private val rawDelayBuffer = ArrayList<Float>()
    private var nativeTse: NativeTSE? = null
    private var initialized = false

    /**
     * Initialize the native TSE engine with assets.
     */
    fun initialize(context: Context): Boolean {
        if (initialized) return true
        
        return try {
            val ntse = NativeTSE()
            // The handover specifies transformer_64d_int8.onnx
            val modelName = "transformer_64d_int8.onnx"
            val modelPath = copyAssetToCache(context, modelName).absolutePath
            val dvectorPath = copyAssetToCache(context, "dvector.bin").absolutePath
            
            // Use CPU for initial stability as requested.
            if (ntse.init(modelPath, dvectorPath)) {
                ntse.reset()
                nativeTse = ntse
                initialized = true
                Log.i(TAG, "Native TSE initialized on CPU using $modelName")
                true
            } else {
                Log.e(TAG, "Failed to initialize Native TSE on CPU")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Native TSE", e)
            false
        }
    }

    /**
     * Process a chunk of raw audio.
     * Returns aligned raw and processed frames of size multiple of frameSize.
     */
    fun processChunk(rawChunk: FloatArray): TseChunkOutput {
        if (rawChunk.isEmpty()) return TseChunkOutput(FloatArray(0), FloatArray(0))

        pendingRaw.addAll(rawChunk.toList())
        if (pendingRaw.size < frameSize) {
            return TseChunkOutput(FloatArray(0), FloatArray(0))
        }

        val rawAlignedList = ArrayList<Float>()
        val processedList = ArrayList<Float>()

        while (pendingRaw.size >= frameSize) {
            val currentFrame = FloatArray(frameSize) { pendingRaw.removeAt(0) }

            // Add current raw frame to delay buffer for alignment
            rawDelayBuffer.addAll(currentFrame.toList())

            // Process with TSE
            val processed = if (initialized) {
                nativeTse?.processFrame(currentFrame) ?: currentFrame.copyOf()
            } else {
                currentFrame.copyOf()
            }
            
            processedList.addAll(processed.toList())

            // Align raw with processed.
            // Since 'processed' at time T corresponds to 'raw' from T - latencySamples,
            // we return 'raw' from the start of our delay buffer.
            if (rawDelayBuffer.size >= frameSize + latencySamples) {
                repeat(frameSize) {
                    rawAlignedList.add(rawDelayBuffer.removeAt(0))
                }
            } else {
                // If we haven't accumulated enough delay yet, we emit zero-aligned raw
                // to keep the output lengths consistent with processedList.
                repeat(frameSize) {
                    rawAlignedList.add(0f)
                }
            }
        }

        return TseChunkOutput(
            rawAligned = rawAlignedList.toFloatArray(),
            processed = processedList.toFloatArray()
        )
    }

    /**
     * Flush remaining samples and reset buffers.
     */
    fun flush(): TseChunkOutput {
        val rawAlignedList = ArrayList<Float>()
        val processedList = ArrayList<Float>()

        // 1. Process any remaining samples in pendingRaw (less than frameSize)
        if (pendingRaw.isNotEmpty()) {
            val tail = FloatArray(frameSize)
            for (i in pendingRaw.indices) {
                tail[i] = pendingRaw[i]
            }
            pendingRaw.clear()
            
            rawDelayBuffer.addAll(tail.toList())
            val processed = if (initialized) {
                nativeTse?.processFrame(tail) ?: tail.copyOf()
            } else {
                tail.copyOf()
            }
            
            processedList.addAll(processed.toList())

            // Align raw with processed.
            if (rawDelayBuffer.size >= frameSize + latencySamples) {
                repeat(frameSize) {
                    rawAlignedList.add(rawDelayBuffer.removeAt(0))
                }
            } else {
                repeat(frameSize) {
                    rawAlignedList.add(0f)
                }
            }
        }

        // 2. Emit the rest of the rawDelayBuffer to catch up.
        // For each sample remaining in rawDelayBuffer, we emit it and a 0 in processed.
        while (rawDelayBuffer.isNotEmpty()) {
            val count = minOf(rawDelayBuffer.size, frameSize)
            repeat(count) {
                rawAlignedList.add(rawDelayBuffer.removeAt(0))
                processedList.add(0f)
            }
        }

        return TseChunkOutput(
            rawAligned = rawAlignedList.toFloatArray(),
            processed = processedList.toFloatArray()
        )
    }

    private fun copyAssetToCache(context: Context, assetName: String): File {
        val targetDir = File(context.cacheDir, "native-tse").apply { mkdirs() }
        val target = File(targetDir, assetName)
        context.assets.open(assetName).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return target
    }

    fun reset() {
        pendingRaw.clear()
        rawDelayBuffer.clear()
        nativeTse?.reset()
    }

    fun release() {
        nativeTse?.release()
        nativeTse = null
        initialized = false
    }
}
