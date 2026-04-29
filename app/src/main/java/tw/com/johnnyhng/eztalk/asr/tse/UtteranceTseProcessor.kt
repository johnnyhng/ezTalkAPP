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
            check(engine.isOpen()) { "ORT session is not open" }
            val dvector = engine.loadDvector(dvectorPath)
            val stftResult = signalProcessor.stft(rawAudio)
            val frames = stftResult.frames
            val timeSteps = frames.size
            if (timeSteps == 0) {
                return UtteranceTseResult(processedAudio = rawAudio, usedFallback = true, reason = "empty_frames")
            }
            val magnitudes = signalProcessor.magnitudes(frames)
            val phases = signalProcessor.phases(frames)
            val modelInput = signalProcessor.toModelInput(frames)
            val featureShape = signalProcessor.featureTensorShape(timeSteps)

            val processedAudio = engine.createFeatureTensor(modelInput, featureShape).use { xTensor ->
                engine.createEmbeddingTensor(dvector).use { embedTensor ->
                    val maskData = engine.runMaskInference(
                        xTensor = xTensor,
                        embedTensor = embedTensor
                    )
                    val maskedMagnitudes = signalProcessor.applyMask(magnitudes, maskData)
                    signalProcessor.reconstruct(
                        maskedMagnitudeFrames = maskedMagnitudes,
                        phaseFrames = phases,
                        originalLength = stftResult.originalLength
                    )
                }
            }
            logInfo("UtteranceTseProcessor completed: audioSamples=${rawAudio.size}, timeSteps=$timeSteps, dvectorSize=${dvector.size}, processedSamples=${processedAudio.size}")
            UtteranceTseResult(
                processedAudio = processedAudio,
                usedFallback = false
            )
        } catch (t: Throwable) {
            logError("UtteranceTseProcessor failed, falling back to raw audio", t)
            UtteranceTseResult(
                processedAudio = rawAudio,
                usedFallback = true,
                reason = t.message
            )
        }
    }

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun logError(message: String, throwable: Throwable) {
        runCatching { Log.e(TAG, message, throwable) }
    }
}
