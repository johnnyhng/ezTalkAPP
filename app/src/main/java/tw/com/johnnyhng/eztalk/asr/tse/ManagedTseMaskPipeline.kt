package tw.com.johnnyhng.eztalk.asr.tse

import android.content.Context

/**
 * Minimal managed-runtime validation chain:
 *
 * `160-sample hop -> STFT magnitude frame -> TFLite mask inference`
 *
 * This class still stops at mask production. It does not reconstruct waveform.
 */
internal class ManagedTseMaskPipeline(
    context: Context,
    private val stftHelper: ManagedStftHelper = ManagedStftHelper(),
    private val frameRunner: ManagedTseFrameRunner = ManagedTseFrameRunner(context)
) {
    internal data class MaskFrameResult(
        val magnitude: FloatArray,
        val phase: FloatArray,
        val mask: FloatArray
    )

    suspend fun initialize(
        modelAssetName: String = "voice_filter_lite.tflite",
        dvectorAssetName: String = "dvector.bin"
    ): Boolean {
        reset()
        return frameRunner.initialize(
            modelAssetName = modelAssetName,
            dvectorAssetName = dvectorAssetName
        )
    }

    fun processHop(inputHop: FloatArray): MaskFrameResult? {
        val stftFrame = stftHelper.processHop(inputHop)
        val mask = frameRunner.processMagnitudeFrame(stftFrame.magnitude) ?: return null
        return MaskFrameResult(
            magnitude = stftFrame.magnitude,
            phase = stftFrame.phase,
            mask = mask
        )
    }

    fun reset() {
        stftHelper.reset()
        frameRunner.reset()
    }

    fun close() {
        frameRunner.close()
        stftHelper.reset()
    }
}
