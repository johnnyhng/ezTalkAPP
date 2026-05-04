package tw.com.johnnyhng.eztalk.asr.tse

internal data class TseChunkOutput(
    val rawAligned: FloatArray,
    val processed: FloatArray
)

/**
 * Placeholder realtime TSE preprocessor entry point.
 *
 * The native realtime backend has been removed. Until the managed realtime TSE path is wired in,
 * this preprocessor preserves the raw/processed contract with processed == raw.
 */
internal class TseAudioPreprocessor(
    private val frameSize: Int = 160
) {
    private val pending = ArrayList<Float>()

    fun processChunk(rawChunk: FloatArray): TseChunkOutput {
        if (rawChunk.isEmpty()) return TseChunkOutput(FloatArray(0), FloatArray(0))

        pending.addAll(rawChunk.toList())
        if (pending.size < frameSize) {
            return TseChunkOutput(FloatArray(0), FloatArray(0))
        }

        val rawOutput = ArrayList<Float>()
        while (pending.size >= frameSize) {
            val rawFrame = pending.subList(0, frameSize).toFloatArray()
            pending.subList(0, frameSize).clear()
            rawOutput.addAll(rawFrame.toList())
        }
        val raw = rawOutput.toFloatArray()
        return TseChunkOutput(
            rawAligned = raw,
            processed = raw.copyOf()
        )
    }

    fun flush(): TseChunkOutput {
        if (pending.isEmpty()) return TseChunkOutput(FloatArray(0), FloatArray(0))
        val rawTail = pending.toFloatArray()
        pending.clear()
        return TseChunkOutput(
            rawAligned = rawTail,
            processed = rawTail.copyOf()
        )
    }

    fun reset() {
        pending.clear()
    }
}
