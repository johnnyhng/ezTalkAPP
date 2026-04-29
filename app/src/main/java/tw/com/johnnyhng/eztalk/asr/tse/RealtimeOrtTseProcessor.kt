package tw.com.johnnyhng.eztalk.asr.tse

import ai.onnxruntime.OnnxTensor
import android.util.Log
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import org.jtransforms.fft.FloatFFT_1D
import tw.com.johnnyhng.eztalk.asr.TAG

internal data class RealtimeTseChunkResult(
    val processedAudio: FloatArray,
    val usedFallback: Boolean,
    val reason: String? = null
)

internal class RealtimeOrtTseProcessor(
    private val engine: OrtTseEngine,
    dvectorPath: String,
    private val fftSize: Int = 512,
    private val hopLength: Int = 160,
    private val winLength: Int = 400,
    private val contextFrames: Int = 64
) : AutoCloseable {
    private val fft = FloatFFT_1D(fftSize.toLong())
    private val freqBins = (fftSize / 2) + 1
    private val window = hannWindow(winLength)
    private val inputBuffer = FloatArray(winLength)
    private val outputBuffer = FloatArray(winLength)
    private val magHistory = FloatArray(contextFrames * freqBins)
    private val dvector = engine.loadDvector(dvectorPath)
    private val embedTensor: OnnxTensor = engine.createEmbeddingTensor(dvector)
    private var processedHopCount = 0

    fun process(rawAudio: FloatArray): RealtimeTseChunkResult {
        if (rawAudio.isEmpty()) {
            return RealtimeTseChunkResult(processedAudio = rawAudio, usedFallback = false)
        }
        return try {
            check(engine.isOpen()) { "ORT session is not open" }
            val output = FloatArray(rawAudio.size)
            var inputOffset = 0
            var outputOffset = 0
            while (inputOffset < rawAudio.size) {
                val actualSize = min(hopLength, rawAudio.size - inputOffset)
                val hopChunk = FloatArray(hopLength)
                rawAudio.copyInto(
                    destination = hopChunk,
                    destinationOffset = 0,
                    startIndex = inputOffset,
                    endIndex = inputOffset + actualSize
                )
                val processedHop = processHop(hopChunk)
                processedHop.copyInto(
                    destination = output,
                    destinationOffset = outputOffset,
                    startIndex = 0,
                    endIndex = actualSize
                )
                inputOffset += actualSize
                outputOffset += actualSize
            }
            RealtimeTseChunkResult(processedAudio = output, usedFallback = false)
        } catch (t: Throwable) {
            RealtimeTseChunkResult(
                processedAudio = rawAudio,
                usedFallback = true,
                reason = t.message
            )
        }
    }

    private fun processHop(audioChunk: FloatArray): FloatArray {
        require(audioChunk.size == hopLength) {
            "Unexpected chunk size: ${audioChunk.size}, expected $hopLength"
        }

        inputBuffer.copyInto(
            destination = inputBuffer,
            destinationOffset = 0,
            startIndex = hopLength,
            endIndex = winLength
        )
        audioChunk.copyInto(
            destination = inputBuffer,
            destinationOffset = winLength - hopLength,
            startIndex = 0,
            endIndex = hopLength
        )

        val complexFrame = FloatArray(fftSize * 2)
        for (index in 0 until winLength) {
            complexFrame[2 * index] = inputBuffer[index] * window[index]
        }
        fft.complexForward(complexFrame)

        val magnitude = FloatArray(freqBins)
        val phase = FloatArray(freqBins)
        for (bin in 0 until freqBins) {
            val real = complexFrame[2 * bin]
            val imag = complexFrame[(2 * bin) + 1]
            magnitude[bin] = hypot(real, imag)
            phase[bin] = kotlin.math.atan2(imag, real)
        }

        magHistory.copyInto(
            destination = magHistory,
            destinationOffset = 0,
            startIndex = freqBins,
            endIndex = magHistory.size
        )
        magnitude.copyInto(
            destination = magHistory,
            destinationOffset = magHistory.size - freqBins,
            startIndex = 0,
            endIndex = freqBins
        )

        val mask = engine.createFeatureTensor(
            features = magHistory,
            shape = longArrayOf(1, 1, contextFrames.toLong(), freqBins.toLong())
        ).use { xTensor ->
            engine.runMaskInference(
                xTensor = xTensor,
                embedTensor = embedTensor
            )
        }

        val estimatedMagnitude = FloatArray(freqBins)
        val lastFrameOffset = contextFrames - 1
        for (bin in 0 until freqBins) {
            estimatedMagnitude[bin] = magnitude[bin] * mask[(bin * contextFrames) + lastFrameOffset]
        }

        val estimatedComplex = FloatArray(fftSize * 2)
        for (bin in 0 until freqBins) {
            estimatedComplex[2 * bin] = estimatedMagnitude[bin] * cos(phase[bin])
            estimatedComplex[(2 * bin) + 1] = estimatedMagnitude[bin] * sin(phase[bin])
        }
        for (bin in 1 until freqBins - 1) {
            val mirrorBin = fftSize - bin
            estimatedComplex[2 * mirrorBin] = estimatedComplex[2 * bin]
            estimatedComplex[(2 * mirrorBin) + 1] = -estimatedComplex[(2 * bin) + 1]
        }

        fft.complexInverse(estimatedComplex, true)
        for (index in 0 until winLength) {
            outputBuffer[index] += estimatedComplex[2 * index] * window[index]
        }

        val outChunk = outputBuffer.copyOfRange(0, hopLength)
        outputBuffer.copyInto(
            destination = outputBuffer,
            destinationOffset = 0,
            startIndex = hopLength,
            endIndex = winLength
        )
        outputBuffer.fill(0f, winLength - hopLength, winLength)
        processedHopCount += 1
        if (processedHopCount <= 5 || processedHopCount % 50 == 0) {
            val maskStats = summarize(mask, lastFrameOffset)
            val inputStats = summarize(audioChunk)
            val outputStats = summarize(outChunk)
            val inputMagStats = summarize(magnitude)
            val estimatedMagStats = summarize(estimatedMagnitude)
            Log.i(
                TAG,
                "RealtimeOrtTseProcessor hop=$processedHopCount inputRms=${inputStats.rms.format3()} outputRms=${outputStats.rms.format3()} diffRms=${rmsDiff(audioChunk, outChunk).format3()} mask[min=${maskStats.min.format3()}, avg=${maskStats.avg.format3()}, max=${maskStats.max.format3()}] magInAvg=${inputMagStats.avg.format3()} magOutAvg=${estimatedMagStats.avg.format3()}"
            )
        }
        return outChunk
    }

    override fun close() {
        embedTensor.close()
    }

    private fun hannWindow(size: Int): FloatArray {
        if (size <= 1) return FloatArray(size) { 1f }
        return FloatArray(size) { index ->
            (0.5 - 0.5 * cos((2.0 * Math.PI * index) / (size - 1))).toFloat()
        }
    }

    private data class Stats(val min: Float, val avg: Float, val max: Float, val rms: Float = avg)

    private fun summarize(values: FloatArray): Stats {
        if (values.isEmpty()) return Stats(0f, 0f, 0f, 0f)
        var minValue = Float.POSITIVE_INFINITY
        var maxValue = Float.NEGATIVE_INFINITY
        var sum = 0.0
        var sumSquares = 0.0
        for (value in values) {
            if (value < minValue) minValue = value
            if (value > maxValue) maxValue = value
            sum += value
            sumSquares += value * value
        }
        return Stats(
            min = minValue,
            avg = (sum / values.size).toFloat(),
            max = maxValue,
            rms = kotlin.math.sqrt(sumSquares / values.size).toFloat()
        )
    }

    private fun summarize(mask: FloatArray, timeIndex: Int): Stats {
        val currentMask = FloatArray(freqBins) { bin -> mask[(bin * contextFrames) + timeIndex] }
        return summarize(currentMask)
    }

    private fun rmsDiff(left: FloatArray, right: FloatArray): Float {
        if (left.isEmpty() || right.isEmpty()) return 0f
        val count = min(left.size, right.size)
        var sumSquares = 0.0
        for (index in 0 until count) {
            val diff = left[index] - right[index]
            sumSquares += diff * diff
        }
        return kotlin.math.sqrt(sumSquares / count).toFloat()
    }

    private fun Float.format3(): String = String.format(java.util.Locale.US, "%.3f", this)
}
