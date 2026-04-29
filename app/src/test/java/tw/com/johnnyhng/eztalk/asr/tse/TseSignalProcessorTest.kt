package tw.com.johnnyhng.eztalk.asr.tse

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TseSignalProcessorTest {
    @Test
    fun modelInputShapeMatchesVoiceFilterLayout() {
        val processor = TseSignalProcessor()
        val audio = FloatArray(1600) { index ->
            sin((2.0 * PI * 440.0 * index) / 16000.0).toFloat()
        }

        val stft = processor.stft(audio)
        val input = processor.toModelInput(stft.frames)

        assertEquals(10, stft.frames.size)
        assertArrayEquals(longArrayOf(1, 1, 10, 257), processor.featureTensorShape(stft.frames.size))
        assertEquals(10 * 257, input.size)
    }

    @Test
    fun reconstructKeepsSignalEnergyWithUnityMask() {
        val processor = TseSignalProcessor()
        val audio = FloatArray(1600) { index ->
            (0.5 * sin((2.0 * PI * 440.0 * index) / 16000.0)).toFloat()
        }

        val stft = processor.stft(audio)
        val reconstructed = processor.reconstruct(
            maskedMagnitudeFrames = processor.magnitudes(stft.frames),
            phaseFrames = processor.phases(stft.frames),
            originalLength = stft.originalLength
        )

        assertEquals(audio.size, reconstructed.size)
        val meanAbsDiff = audio.indices
            .map { index -> abs(audio[index] - reconstructed[index]) }
            .average()
        assertTrue("Reconstruction drift is too large: $meanAbsDiff", meanAbsDiff < 0.05)
    }
}
