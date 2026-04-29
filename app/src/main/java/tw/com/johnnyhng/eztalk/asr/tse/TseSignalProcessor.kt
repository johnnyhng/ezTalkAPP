package tw.com.johnnyhng.eztalk.asr.tse

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import org.jtransforms.fft.FloatFFT_1D

internal data class StftFrame(
    val magnitude: FloatArray,
    val phase: FloatArray
)

internal data class StftResult(
    val frames: List<StftFrame>,
    val originalLength: Int
)

internal class TseSignalProcessor(
    private val fftSize: Int = 512,
    private val hopLength: Int = 160,
    private val winLength: Int = 400
) {
    private val fft = FloatFFT_1D(fftSize.toLong())
    private val freqBins = (fftSize / 2) + 1
    private val window by lazy { hannWindow(winLength) }

    fun stft(samples: FloatArray): StftResult {
        if (samples.isEmpty()) {
            return StftResult(frames = emptyList(), originalLength = 0)
        }
        val frames = ArrayList<StftFrame>()
        var offset = 0
        while (offset < samples.size) {
            val complexFrame = FloatArray(fftSize * 2)
            val copyLength = min(winLength, samples.size - offset)
            for (index in 0 until copyLength) {
                complexFrame[2 * index] = samples[offset + index] * window[index]
            }
            fft.complexForward(complexFrame)
            frames += complexFrameToMagnitudePhase(complexFrame)
            offset += hopLength
        }
        return StftResult(frames = frames, originalLength = samples.size)
    }

    fun toModelInput(frames: List<StftFrame>): FloatArray {
        val timeSteps = frames.size
        val output = FloatArray(timeSteps * freqBins)
        frames.forEachIndexed { timeIndex, frame ->
            require(frame.magnitude.size == freqBins) {
                "Unexpected freq bin count: ${frame.magnitude.size}"
            }
            frame.magnitude.copyInto(
                destination = output,
                destinationOffset = timeIndex * freqBins
            )
        }
        return output
    }

    fun applyMask(
        magnitudes: List<FloatArray>,
        maskData: FloatArray
    ): List<FloatArray> {
        val timeSteps = magnitudes.size
        require(maskData.size == timeSteps * freqBins) {
            "Unexpected mask size: ${maskData.size}, expected ${timeSteps * freqBins}"
        }
        return List(timeSteps) { timeIndex ->
            val masked = FloatArray(freqBins)
            val inputMagnitude = magnitudes[timeIndex]
            for (bin in 0 until freqBins) {
                val maskIndex = (bin * timeSteps) + timeIndex
                masked[bin] = inputMagnitude[bin] * maskData[maskIndex]
            }
            masked
        }
    }

    fun reconstruct(
        maskedMagnitudeFrames: List<FloatArray>,
        phaseFrames: List<FloatArray>,
        originalLength: Int
    ): FloatArray {
        require(maskedMagnitudeFrames.size == phaseFrames.size) {
            "Magnitude and phase frame count mismatch"
        }
        if (maskedMagnitudeFrames.isEmpty() || originalLength == 0) {
            return FloatArray(0)
        }

        val frameCount = maskedMagnitudeFrames.size
        val outputLength = maxOf(originalLength, ((frameCount - 1) * hopLength) + winLength)
        val output = FloatArray(outputLength)
        val overlapWeight = FloatArray(outputLength)

        maskedMagnitudeFrames.indices.forEach { frameIndex ->
            val complexFrame = magnitudePhaseToComplex(
                magnitude = maskedMagnitudeFrames[frameIndex],
                phase = phaseFrames[frameIndex]
            )
            fft.complexInverse(complexFrame, true)
            val frameOffset = frameIndex * hopLength
            for (sampleIndex in 0 until winLength) {
                val outputIndex = frameOffset + sampleIndex
                if (outputIndex >= outputLength) break
                val windowWeight = window[sampleIndex]
                output[outputIndex] += complexFrame[2 * sampleIndex] * windowWeight
                overlapWeight[outputIndex] += windowWeight * windowWeight
            }
        }

        for (index in output.indices) {
            val weight = overlapWeight[index]
            if (weight > 1e-8f) {
                output[index] /= weight
            }
        }

        return if (output.size == originalLength) {
            output
        } else {
            output.copyOf(originalLength)
        }
    }

    fun phases(frames: List<StftFrame>): List<FloatArray> = frames.map { it.phase }

    fun magnitudes(frames: List<StftFrame>): List<FloatArray> = frames.map { it.magnitude }

    fun featureTensorShape(timeSteps: Int): LongArray = longArrayOf(1, 1, timeSteps.toLong(), freqBins.toLong())

    private fun complexFrameToMagnitudePhase(frame: FloatArray): StftFrame {
        val magnitude = FloatArray(freqBins)
        val phase = FloatArray(freqBins)
        for (bin in 0 until freqBins) {
            val real = frame[2 * bin]
            val imag = frame[(2 * bin) + 1]
            magnitude[bin] = hypot(real, imag)
            phase[bin] = kotlin.math.atan2(imag, real)
        }
        return StftFrame(magnitude = magnitude, phase = phase)
    }

    private fun magnitudePhaseToComplex(
        magnitude: FloatArray,
        phase: FloatArray
    ): FloatArray {
        require(magnitude.size == freqBins && phase.size == freqBins) {
            "Unexpected freq bin count"
        }
        val complex = FloatArray(fftSize * 2)
        for (bin in 0 until freqBins) {
            complex[2 * bin] = magnitude[bin] * cos(phase[bin])
            complex[(2 * bin) + 1] = magnitude[bin] * sin(phase[bin])
        }
        for (bin in 1 until freqBins - 1) {
            val mirrorBin = fftSize - bin
            complex[2 * mirrorBin] = complex[2 * bin]
            complex[(2 * mirrorBin) + 1] = -complex[(2 * bin) + 1]
        }
        return complex
    }

    private fun hannWindow(size: Int): FloatArray {
        if (size <= 1) return FloatArray(size) { 1f }
        return FloatArray(size) { index ->
            (0.5 - 0.5 * cos((2.0 * Math.PI * index) / (size - 1))).toFloat()
        }
    }
}
