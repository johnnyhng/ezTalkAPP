package tw.com.johnnyhng.eztalk.asr.tse

import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG

internal data class TseChunkOutput(
    val rawAligned: FloatArray,
    val processed: FloatArray
)

internal class TseAudioPreprocessor(
    private val nativeTse: NativeTSE,
    private val frameSize: Int = 400
) {
    private val pending = ArrayList<Float>()
    private var bypassProcessing = false

    fun processChunk(rawChunk: FloatArray): TseChunkOutput {
        if (rawChunk.isEmpty()) return TseChunkOutput(FloatArray(0), FloatArray(0))
        if (bypassProcessing) return TseChunkOutput(rawChunk.copyOf(), rawChunk.copyOf())

        pending.addAll(rawChunk.toList())
        if (pending.size < frameSize) {
            return TseChunkOutput(FloatArray(0), FloatArray(0))
        }

        val rawOutput = ArrayList<Float>()
        val processedOutput = ArrayList<Float>()
        while (pending.size >= frameSize) {
            val rawFrame = pending.subList(0, frameSize).toFloatArray()
            pending.subList(0, frameSize).clear()
            val processedFrame = processFrameOrFallback(rawFrame)
            rawOutput.addAll(rawFrame.toList())
            processedOutput.addAll(processedFrame.toList())
        }
        return TseChunkOutput(rawOutput.toFloatArray(), processedOutput.toFloatArray())
    }

    fun flush(): TseChunkOutput {
        if (pending.isEmpty()) return TseChunkOutput(FloatArray(0), FloatArray(0))
        val rawTail = pending.toFloatArray()
        pending.clear()

        if (bypassProcessing) {
            return TseChunkOutput(rawTail, rawTail.copyOf())
        }

        val paddedFrame = FloatArray(frameSize)
        rawTail.copyInto(paddedFrame)
        val processedFrame = processFrameOrFallback(paddedFrame)
        return TseChunkOutput(
            rawAligned = rawTail,
            processed = processedFrame.copyOfRange(0, rawTail.size)
        )
    }

    fun processAll(rawAudio: FloatArray): FloatArray {
        if (rawAudio.isEmpty()) return FloatArray(0)
        reset()
        val emitted = processChunk(rawAudio)
        val tail = flush()
        return FloatArray(emitted.processed.size + tail.processed.size).also { merged ->
            emitted.processed.copyInto(merged, endIndex = emitted.processed.size)
            tail.processed.copyInto(merged, destinationOffset = emitted.processed.size)
        }
    }

    fun reset() {
        pending.clear()
    }

    private fun processFrameOrFallback(rawFrame: FloatArray): FloatArray {
        return try {
            val processed = nativeTse.processFrame(rawFrame)
            if (processed == null || processed.size != rawFrame.size) {
                bypassProcessing = true
                Log.w(TAG, "NativeTSE processFrame returned invalid output, switching to raw passthrough")
                rawFrame
            } else {
                processed
            }
        } catch (t: Throwable) {
            bypassProcessing = true
            Log.e(TAG, "NativeTSE processFrame failed, switching to raw passthrough", t)
            rawFrame
        }
    }
}
