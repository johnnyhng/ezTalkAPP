package tw.com.johnnyhng.eztalk.asr.tse

/**
 * App-side helper for the managed TSE path.
 *
 * Responsibilities:
 * - maintain the rolling 32 x 257 CNN window expected by VoiceFilter-Lite
 * - carry LSTM state across frame inferences
 *
 * This class does not perform STFT or model invocation by itself.
 * It only provides the minimum state container needed to feed real frames into
 * [ManagedTseProbe.runSingleFrame].
 */
internal class ManagedTseStreamingState(
    private val contextFrames: Int = 32,
    private val freqBins: Int = 257
) {
    private val cnnWindow = FloatArray(contextFrames * freqBins)
    private var lstmState = ManagedTseProbe.LstmState()

    fun currentWindow(): FloatArray = cnnWindow.copyOf()

    fun currentLstmState(): ManagedTseProbe.LstmState = ManagedTseProbe.LstmState(
        h1 = lstmState.h1.copyOf(),
        c1 = lstmState.c1.copyOf(),
        h2 = lstmState.h2.copyOf(),
        c2 = lstmState.c2.copyOf()
    )

    fun appendMagnitudeFrame(magFrame: FloatArray) {
        require(magFrame.size == freqBins) {
            "Magnitude frame must have $freqBins floats, got ${magFrame.size}"
        }

        val oneFrame = freqBins
        System.arraycopy(
            cnnWindow,
            oneFrame,
            cnnWindow,
            0,
            (contextFrames - 1) * oneFrame
        )
        magFrame.copyInto(
            destination = cnnWindow,
            destinationOffset = (contextFrames - 1) * oneFrame
        )
    }

    fun updateState(nextState: ManagedTseProbe.LstmState) {
        lstmState = ManagedTseProbe.LstmState(
            h1 = nextState.h1.copyOf(),
            c1 = nextState.c1.copyOf(),
            h2 = nextState.h2.copyOf(),
            c2 = nextState.c2.copyOf()
        )
    }

    fun reset() {
        cnnWindow.fill(0f)
        lstmState = ManagedTseProbe.LstmState()
    }
}
