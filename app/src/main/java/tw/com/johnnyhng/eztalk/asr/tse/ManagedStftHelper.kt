package tw.com.johnnyhng.eztalk.asr.tse

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Small app-side STFT helper for managed TSE validation.
 *
 * Priorities:
 * - correct 160-sample hop -> 257-bin magnitude/phase output
 * - self-contained Kotlin implementation
 * - no new heavy FFT dependency
 *
 * This is for validation, not final performance tuning.
 */
internal class ManagedStftHelper(
    private val hopLength: Int = 160,
    private val winLength: Int = 400,
    private val nfft: Int = 512
) {
    companion object {
        private const val SAMPLE_RATE = 16000
    }

    internal data class StftFrame(
        val magnitude: FloatArray,
        val phase: FloatArray
    )

    private val freqBins = nfft / 2 + 1
    private val inputBuffer = FloatArray(winLength)
    private val window = FloatArray(winLength) { idx ->
        (0.5f - 0.5f * cos((2.0 * PI * idx) / (winLength - 1))).toFloat()
    }

    fun processHop(inputHop: FloatArray): StftFrame {
        require(inputHop.size == hopLength) {
            "inputHop must have $hopLength samples, got ${inputHop.size}"
        }

        System.arraycopy(inputBuffer, hopLength, inputBuffer, 0, winLength - hopLength)
        inputHop.copyInto(inputBuffer, destinationOffset = winLength - hopLength)

        val real = DoubleArray(nfft)
        val imag = DoubleArray(nfft)
        for (i in 0 until winLength) {
            real[i] = (inputBuffer[i] * window[i]).toDouble()
        }

        fft(real, imag)

        val magnitude = FloatArray(freqBins)
        val phase = FloatArray(freqBins)
        for (i in 0 until freqBins) {
            magnitude[i] = hypot(real[i], imag[i]).toFloat()
            phase[i] = atan2(imag[i], real[i]).toFloat()
        }
        return StftFrame(magnitude = magnitude, phase = phase)
    }

    fun reset() {
        inputBuffer.fill(0f)
    }

    fun debugDescription(): String {
        return "ManagedStftHelper(sr=$SAMPLE_RATE hop=$hopLength win=$winLength nfft=$nfft bins=$freqBins)"
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        ManagedFft.forward(real, imag)
    }
}
