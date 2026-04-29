package tw.com.johnnyhng.eztalk.asr.tse

import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG

internal object OrtTseDebugRunner {
    fun inspectModel(modelPath: String, dvectorPath: String): OrtTseModelMetadata {
        return OrtTseEngine().use { engine ->
            engine.open(modelPath)
            val metadata = engine.inspectModel()
            val dvector = engine.loadDvector(dvectorPath)
            Log.i(
                TAG,
                "ORT TSE inspect: modelPath=$modelPath, inputs=${metadata.inputs.map { "${it.name}:${it.shape.contentToString()}:${it.type}" }}, outputs=${metadata.outputs.map { "${it.name}:${it.shape.contentToString()}:${it.type}" }}, dvectorSize=${dvector.size}"
            )
            metadata
        }
    }
}
