package tw.com.johnnyhng.eztalk.asr.tse

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object ManagedFft {
    fun forward(real: DoubleArray, imag: DoubleArray) {
        transform(real, imag, inverse = false)
    }

    fun inverse(real: DoubleArray, imag: DoubleArray) {
        transform(real, imag, inverse = true)
        val scale = real.size.toDouble()
        for (i in real.indices) {
            real[i] /= scale
            imag[i] /= scale
        }
    }

    private fun transform(real: DoubleArray, imag: DoubleArray, inverse: Boolean) {
        val n = real.size
        require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be a power of 2" }

        val levels = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) {
            val j = reverseBits(i, levels)
            if (j > i) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var size = 2
        while (size <= n) {
            val halfSize = size / 2
            val tableStep = (if (inverse) 2.0 else -2.0) * PI / size
            var i = 0
            while (i < n) {
                var k = 0
                while (k < halfSize) {
                    val angle = k * tableStep
                    val wr = cos(angle)
                    val wi = sin(angle)

                    val evenIndex = i + k
                    val oddIndex = evenIndex + halfSize
                    val tr = wr * real[oddIndex] - wi * imag[oddIndex]
                    val ti = wr * imag[oddIndex] + wi * real[oddIndex]

                    real[oddIndex] = real[evenIndex] - tr
                    imag[oddIndex] = imag[evenIndex] - ti
                    real[evenIndex] += tr
                    imag[evenIndex] += ti
                    k++
                }
                i += size
            }
            size *= 2
        }
    }

    private fun reverseBits(value: Int, width: Int): Int {
        var x = value
        var y = 0
        repeat(width) {
            y = (y shl 1) or (x and 1)
            x = x ushr 1
        }
        return y
    }
}
