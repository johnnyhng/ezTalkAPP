package tw.com.johnnyhng.eztalk.asr.tse

import android.util.Log

/**
 * Kotlin wrapper for the stateful realtime TSE NDK core.
 *
 * Input contract:
 * - `processFrame()` expects exactly 160 float samples (10 ms @ 16 kHz)
 * - native side maintains the streaming feature/state buffers required by the current TSE backend
 *
 * Lifecycle guidance:
 * - call `reset()` before a new utterance or a new recording session
 * - call `release()` when the engine is no longer needed
 */
class NativeTSE {

    companion object {
        private const val TAG = "NativeTSE"
        private var loadError: UnsatisfiedLinkError? = null

        init {
            try {
                System.loadLibrary("c++_shared")
                System.loadLibrary("onnxruntime")
                System.loadLibrary("oboe")
                System.loadLibrary("tse_engine")
                Log.d(TAG, "NativeTSE libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                loadError = e
                Log.e(TAG, "Failed to load NativeTSE libraries", e)
            }
        }
    }

    init {
        loadError?.let { throw it }
    }

    external fun init(modelPath: String, dvectorPath: String): Boolean

    external fun processFrame(audioFrame: FloatArray): FloatArray?

    external fun reset()

    external fun release()
}
