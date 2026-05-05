package tw.com.johnnyhng.eztalk.asr.tse

import android.content.Context

/**
 * Managed TSE validation chain that continues from mask inference into
 * a minimal overlap-add waveform reconstruction.
 */
internal class ManagedTseWaveformPipeline(
    context: Context,
    private val stftHelper: ManagedStftHelper = ManagedStftHelper(),
    private val frameRunner: ManagedTseFrameRunner = ManagedTseFrameRunner(context),
    private val reconstructor: ManagedTseWaveformReconstructor = ManagedTseWaveformReconstructor()
) {
    internal data class WaveformFrameResult(
        val magnitude: FloatArray,
        val phase: FloatArray,
        val mask: FloatArray,
        val waveformHop: FloatArray,
        val waveformRms: Double,
        val frameRms: Double
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

    fun processHop(inputHop: FloatArray): WaveformFrameResult? {
        val stftFrame = stftHelper.processHop(inputHop)
        val mask = frameRunner.processMagnitudeFrame(stftFrame.magnitude) ?: return null
        val reconstruction = reconstructor.processFrame(
            magnitude = stftFrame.magnitude,
            phase = stftFrame.phase,
            mask = mask
        ) ?: return null
        return WaveformFrameResult(
            magnitude = stftFrame.magnitude,
            phase = stftFrame.phase,
            mask = mask,
            waveformHop = reconstruction.waveformHop,
            waveformRms = reconstruction.waveformRms,
            frameRms = reconstruction.frameRms
        )
    }

    fun reset() {
        stftHelper.reset()
        frameRunner.reset()
        reconstructor.reset()
    }

    fun close() {
        frameRunner.close()
        stftHelper.reset()
        reconstructor.reset()
    }
}
