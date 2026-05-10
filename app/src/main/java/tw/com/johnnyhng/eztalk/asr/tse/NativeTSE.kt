package tw.com.johnnyhng.eztalk.asr.tse

import android.util.Log

/**
 * NativeTSE: Kotlin wrapper for the stateful realtime TSE NDK core.
 *
 * Input contract:
 * - `processFrame()` expects exactly 160 float samples (10 ms @ 16 kHz)
 * - native side maintains internal 64-frame context, streaming buffers, and warm-up state
 *
 * Lifecycle guidance:
 * - call `reset()` before a new utterance or a new recording session
 * - call `release()` when the engine is no longer needed
 */
class NativeTSE {

    companion object {
        private const val TAG = "NativeTSE"

        init {
            try {
                // Load dependencies first.
                System.loadLibrary("onnxruntime")
                // Load the JNI core engine.
                System.loadLibrary("tse_engine")
                Log.d(TAG, "NativeTSE libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load NativeTSE libraries: ${e.message}")
            }
        }
    }

    /**
     * Initialize the TSE Engine with model and d-vector
     * @param modelPath Absolute path to .onnx model
     * @param dvectorPath Absolute path to .bin d-vector
     * @return Boolean success
     */
    external fun init(modelPath: String, dvectorPath: String): Boolean

    /**
     * Process a single 10 ms audio hop.
     *
     * The native engine keeps its own analysis buffer, synthesis overlap buffer,
     * speaker context history, and warm-up strategy internally.
     *
     * @param audioFrame FloatArray of length 160 (10 ms @ 16 kHz)
     * @return Processed FloatArray or null if failed
     */
    external fun processFrame(audioFrame: FloatArray): FloatArray?

    /**
     * Reset internal state (buffers and history) for a new utterance
     */
    external fun reset()

    /**
     * Stop the audio engine and release resources
     */
    external fun release()
}
