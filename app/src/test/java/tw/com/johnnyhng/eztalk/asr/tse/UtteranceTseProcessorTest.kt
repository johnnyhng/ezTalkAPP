package tw.com.johnnyhng.eztalk.asr.tse

import ai.onnxruntime.OrtEnvironment
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceTseProcessorTest {
    @Test
    fun runsVoiceFilterModelEndToEnd() {
        val modelPath = "src/main/assets/voice_filter_int8.onnx"
        val dvectorPath = "src/main/assets/dvector.bin"
        val audio = FloatArray(3200) { index ->
            val t = index / 16000.0
            (0.35 * sin(2.0 * PI * 220.0 * t) + 0.2 * sin(2.0 * PI * 440.0 * t)).toFloat()
        }

        val result = OrtTseEngine(OrtEnvironment.getEnvironment()).use { engine ->
            engine.open(modelPath)
            UtteranceTseProcessor(engine).process(audio, dvectorPath)
        }

        assertFalse("Expected ORT TSE to run without fallback, reason=${result.reason}", result.usedFallback)
        assertEquals(audio.size, result.processedAudio.size)
        val meanAbsValue = result.processedAudio.map { abs(it) }.average()
        assertTrue("Processed audio should not be silent", meanAbsValue > 1e-5)
    }
}
