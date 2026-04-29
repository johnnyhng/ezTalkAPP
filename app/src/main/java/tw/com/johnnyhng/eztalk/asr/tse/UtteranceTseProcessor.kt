package tw.com.johnnyhng.eztalk.asr.tse

import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG

internal data class UtteranceTseResult(
    val processedAudio: FloatArray,
    val usedFallback: Boolean,
    val reason: String? = null
)

internal class UtteranceTseProcessor(
    private val engine: OrtTseEngine,
    private val signalProcessor: TseSignalProcessor = TseSignalProcessor()
) {
    fun process(rawAudio: FloatArray, dvectorPath: String): UtteranceTseResult {
        if (rawAudio.isEmpty()) {
            return UtteranceTseResult(processedAudio = rawAudio, usedFallback = true, reason = "empty_audio")
        }
        return try {
            val dvector = engine.loadDvector(dvectorPath)
            Log.i(TAG, "UtteranceTseProcessor ready: audioSamples=${rawAudio.size}, dvectorSize=${dvector.size}")
            // Phase 1 only: metadata and input loading are validated here.
            // Full utterance-level DSP + ORT inference will be implemented in Phase 2.
            signalProcessor.stft(rawAudio)
            UtteranceTseResult(
                processedAudio = rawAudio,
                usedFallback = true,
                reason = "phase1_placeholder"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "UtteranceTseProcessor failed, falling back to raw audio", t)
            UtteranceTseResult(
                processedAudio = rawAudio,
                usedFallback = true,
                reason = t.message
            )
        }
    }
}
