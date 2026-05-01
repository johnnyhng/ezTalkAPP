package tw.com.johnnyhng.eztalk.asr.tse

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Minimal overlap-add reconstructor for the managed TSE validation path.
 *
 * This is intentionally simple and debug-oriented:
 * - reconstructs one 400-sample frame from masked magnitude + phase
 * - applies a Hann synthesis window
 * - overlap-adds into an internal buffer
 * - emits the next 160-sample hop
 */
internal class ManagedTseWaveformReconstructor(
    private val winLength: Int = 400,
    private val hopLength: Int = 160,
    private val nfft: Int = 512
) {
    private val freqBins = nfft / 2 + 1
    private val synthesisWindow = FloatArray(winLength) { idx ->
        (0.5f - 0.5f * kotlin.math.cos((2.0 * kotlin.math.PI * idx) / (winLength - 1))).toFloat()
    }
    private val olaBuffer = FloatArray(winLength)
    private val olaWeight = FloatArray(winLength)

    internal data class ReconstructedHop(
        val waveformHop: FloatArray,
        val waveformRms: Double,
        val frameRms: Double
    )

    fun processFrame(
        magnitude: FloatArray,
        phase: FloatArray,
        mask: FloatArray
    ): ReconstructedHop? {
        require(magnitude.size == freqBins) {
            "magnitude must have $freqBins bins, got ${magnitude.size}"
        }
        require(phase.size == freqBins) {
            "phase must have $freqBins bins, got ${phase.size}"
        }
        require(mask.size == freqBins) {
            "mask must have $freqBins bins, got ${mask.size}"
        }

        val real = DoubleArray(nfft)
        val imag = DoubleArray(nfft)
        for (bin in 0 until freqBins) {
            val maskedMag = magnitude[bin] * mask[bin]
            val angle = phase[bin].toDouble()
            real[bin] = maskedMag * cos(angle)
            imag[bin] = maskedMag * sin(angle)
        }
        for (bin in 1 until freqBins - 1) {
            val mirror = nfft - bin
            real[mirror] = real[bin]
            imag[mirror] = -imag[bin]
        }

        ManagedFft.inverse(real, imag)

        var frameEnergy = 0.0
        for (i in 0 until winLength) {
            val sample = (real[i].toFloat() * synthesisWindow[i])
            olaBuffer[i] += sample
            olaWeight[i] += synthesisWindow[i]
            frameEnergy += sample * sample
        }

        val waveformHop = FloatArray(hopLength)
        var hopEnergy = 0.0
        for (i in 0 until hopLength) {
            val weight = olaWeight[i]
            val sample = if (weight > 1e-6f) olaBuffer[i] / weight else olaBuffer[i]
            waveformHop[i] = sample
            hopEnergy += sample * sample
        }

        shiftBuffers()

        return ReconstructedHop(
            waveformHop = waveformHop,
            waveformRms = kotlin.math.sqrt(hopEnergy / hopLength),
            frameRms = kotlin.math.sqrt(frameEnergy / winLength)
        )
    }

    fun reset() {
        olaBuffer.fill(0f)
        olaWeight.fill(0f)
    }

    private fun shiftBuffers() {
        if (hopLength >= winLength) {
            olaBuffer.fill(0f)
            olaWeight.fill(0f)
            return
        }
        System.arraycopy(olaBuffer, hopLength, olaBuffer, 0, winLength - hopLength)
        System.arraycopy(olaWeight, hopLength, olaWeight, 0, winLength - hopLength)
        for (i in winLength - hopLength until winLength) {
            olaBuffer[i] = 0f
            olaWeight[i] = 0f
        }
    }
}
