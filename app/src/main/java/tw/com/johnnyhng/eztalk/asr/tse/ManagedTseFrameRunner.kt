package tw.com.johnnyhng.eztalk.asr.tse

import android.content.Context

/**
 * Minimal app-side frame runner for the managed TSE path.
 *
 * This class intentionally stops at mask inference. It does not yet perform:
 * - STFT generation
 * - mask application to waveform
 * - ISTFT / overlap-add reconstruction
 *
 * It exists to bridge the gap between:
 * - model creation/invocation
 * - real per-frame managed inference
 */
internal interface ManagedTseMaskFrameRunner {
    suspend fun initialize(
        modelAssetName: String,
        dvectorAssetName: String
    ): Boolean

    fun processMagnitudeFrame(magFrame: FloatArray): FloatArray?

    fun reset()

    fun close()
}

internal class ManagedTseFrameRunner(
    context: Context,
    private val probe: ManagedTseProbe = ManagedTseProbe(context),
    private val streamingState: ManagedTseStreamingState = ManagedTseStreamingState()
) : ManagedTseMaskFrameRunner {
    private var embed: FloatArray? = null

    override suspend fun initialize(
        modelAssetName: String,
        dvectorAssetName: String
    ): Boolean {
        val ok = probe.initialize(modelAssetName)
        if (!ok) return false
        embed = probe.loadDvectorForTesting(dvectorAssetName)
        streamingState.reset()
        return true
    }

    override fun processMagnitudeFrame(magFrame: FloatArray): FloatArray? {
        val localEmbed = embed ?: return null
        streamingState.appendMagnitudeFrame(magFrame)
        val result = probe.runSingleFrame(
            cnnWindow = streamingState.currentWindow(),
            embed = localEmbed,
            state = streamingState.currentLstmState()
        ) ?: return null
        streamingState.updateState(result.nextState)
        return result.mask
    }

    override fun reset() {
        streamingState.reset()
    }

    override fun close() {
        probe.close()
        embed = null
        streamingState.reset()
    }
}
