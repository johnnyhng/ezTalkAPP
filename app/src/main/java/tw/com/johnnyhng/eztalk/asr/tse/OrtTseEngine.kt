package tw.com.johnnyhng.eztalk.asr.tse

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal data class OrtTensorMetadata(
    val name: String,
    val shape: LongArray,
    val type: OnnxJavaType
)

internal data class OrtTseModelMetadata(
    val inputs: List<OrtTensorMetadata>,
    val outputs: List<OrtTensorMetadata>
)

internal class OrtTseEngine(
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
) : AutoCloseable {
    private var session: OrtSession? = null

    fun open(modelPath: String) {
        close()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(1)
        }
        session = environment.createSession(modelPath, options)
    }

    fun isOpen(): Boolean = session != null

    fun inspectModel(): OrtTseModelMetadata {
        val activeSession = requireNotNull(session) { "ORT session is not open" }
        val inputs = activeSession.inputInfo.map { (name, nodeInfo) ->
            val tensorInfo = nodeInfo.info as? TensorInfo
                ?: error("Input '$name' is not a tensor")
            OrtTensorMetadata(name = name, shape = tensorInfo.shape, type = tensorInfo.type)
        }
        val outputs = activeSession.outputInfo.map { (name, nodeInfo) ->
            val tensorInfo = nodeInfo.info as? TensorInfo
                ?: error("Output '$name' is not a tensor")
            OrtTensorMetadata(name = name, shape = tensorInfo.shape, type = tensorInfo.type)
        }
        return OrtTseModelMetadata(inputs = inputs, outputs = outputs)
    }

    fun loadDvector(dvectorPath: String): FloatArray {
        val bytes = File(dvectorPath).readBytes()
        require(bytes.size % Float.SIZE_BYTES == 0) {
            "Invalid dvector byte size: ${bytes.size}"
        }
        val floatBuffer = ByteBuffer
            .wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
        return FloatArray(floatBuffer.remaining()).also(floatBuffer::get)
    }

    fun createEmbeddingTensor(dvector: FloatArray): OnnxTensor {
        return OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(dvector),
            longArrayOf(1, dvector.size.toLong())
        )
    }

    fun createFeatureTensor(features: FloatArray, shape: LongArray): OnnxTensor {
        return OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(features),
            shape
        )
    }

    fun runMaskInference(
        xTensor: OnnxTensor,
        embedTensor: OnnxTensor,
        xInputName: String = "x",
        embedInputName: String = "embed",
        outputName: String = "mask"
    ): FloatArray {
        val activeSession = requireNotNull(session) { "ORT session is not open" }
        activeSession.run(
            mapOf(
                xInputName to xTensor,
                embedInputName to embedTensor
            ),
            setOf(outputName)
        ).use { results ->
            val outputEntry = results.firstOrNull()
                ?: throw OrtException("Missing ORT output '$outputName'")
            val outputTensor = outputEntry.value as? OnnxTensor
                ?: throw OrtException("Unexpected ORT output type: ${outputEntry.value.javaClass.name}")
            val buffer = outputTensor.floatBuffer
            return FloatArray(buffer.remaining()).also(buffer::get)
        }
    }

    override fun close() {
        session?.close()
        session = null
    }
}
