package tw.com.johnnyhng.eztalk.asr.tse

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFilterModelMetadataTest {
    @Test
    fun inspectVoiceFilterModelMetadata() {
        val modelFile = File("src/main/assets/voice_filter_int8.onnx")
        val dvectorFile = File("src/main/assets/dvector.bin")
        assertTrue("voice_filter_int8.onnx should exist", modelFile.exists())
        assertTrue("dvector.bin should exist", dvectorFile.exists())

        val environment = OrtEnvironment.getEnvironment()
        val session = environment.createSession(
            modelFile.absolutePath,
            OrtSession.SessionOptions()
        )

        session.use {
            val inputs = session.inputInfo.mapValues { (_, nodeInfo) ->
                nodeInfo.info as TensorInfo
            }
            val outputs = session.outputInfo.mapValues { (_, nodeInfo) ->
                nodeInfo.info as TensorInfo
            }

            println("VoiceFilter inputs:")
            inputs.forEach { (name, info) ->
                println("  $name shape=${info.shape.contentToString()} type=${info.type}")
            }
            println("VoiceFilter outputs:")
            outputs.forEach { (name, info) ->
                println("  $name shape=${info.shape.contentToString()} type=${info.type}")
            }

            val dvectorBytes = dvectorFile.readBytes()
            assertEquals("dvector.bin should contain 192 float32 values", 192 * Float.SIZE_BYTES, dvectorBytes.size)
            val dvector = ByteBuffer
                .wrap(dvectorBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer()
            assertEquals("dvector should decode to 192 floats", 192, dvector.remaining())
        }
    }
}
