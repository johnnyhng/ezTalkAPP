package tw.com.johnnyhng.eztalk.asr.tse

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.jtransforms.fft.FloatFFT_1D

internal data class StftFrame(
    val magnitude: FloatArray,
    val phase: FloatArray
)

internal class TseSignalProcessor(
    private val fftSize: Int = 512,
    private val hopLength: Int = 160,
    private val winLength: Int = 400
) {
    private val fft = FloatFFT_1D(fftSize.toLong())
    private val window by lazy { hannWindow(winLength) }

    fun stftFrames(samples: FloatArray): List<StftFrame> {
        if (samples.isEmpty()) return emptyList()
        val frames = ArrayList<StftFrame>()
        var offset = 0
        while (offset < samples.size) {
            val frame = FloatArray(fftSize)
            val copyLength = minOf(winLength, samples.size - offset)
            for (index in 0 until copyLength) {
                frame[index] = samples[offset + index] * window[index]
            }
            fft.realForward(frame)
            frames += frameToMagnitudePhase(frame)
            offset += hopLength
        }
        return frames
    }

    fun reconstructFromMaskedFrames(
        maskedMagnitudeFrames: List<FloatArray>,
        phaseFrames: List<FloatArray>,
        originalLength: Int
    ): FloatArray {
        require(maskedMagnitudeFrames.size == phaseFrames.size) {
            "Magnitude and phase frame count mismatch"
        }
        // Phase 1 only: keep the contract and fall back to a silent placeholder
        // until overlap-add reconstruction is implemented.
        return FloatArray(originalLength)
    }

    private fun frameToMagnitudePhase(frame: FloatArray): StftFrame {
        val freqBins = (fftSize / 2) + 1
        val magnitude = FloatArray(freqBins)
        val phase = FloatArray(freqBins)
        magnitude[0] = kotlin.math.abs(frame[0])
        phase[0] = 0f
        for (bin in 1 until freqBins - 1) {
            val real = frame[2 * bin]
            val imag = frame[2 * bin + 1]
            magnitude[bin] = hypot(real, imag)
            phase[bin] = kotlin.math.atan2(imag, real)
        }
        magnitude[freqBins - 1] = kotlin.math.abs(frame[1])
        phase[freqBins - 1] = 0f
        return StftFrame(magnitude = magnitude, phase = phase)
    }

    private fun hannWindow(size: Int): FloatArray {
        if (size <= 1) return FloatArray(size) { 1f }
        return FloatArray(size) { index ->
            (0.5 - 0.5 * cos((2.0 * Math.PI * index) / (size - 1))).toFloat()
        }
    }
}
