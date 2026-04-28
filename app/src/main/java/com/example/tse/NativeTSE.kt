package com.example.tse

import android.util.Log

/**
 * JNI-bound class name must stay aligned with libtse_engine.so symbols.
 */
class NativeTSE {

    companion object {
        private const val TAG = "NativeTSE"
        private var loadError: UnsatisfiedLinkError? = null
        val isNativeAvailable: Boolean
            get() = loadError == null

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

    private fun ensureLoaded() {
        loadError?.let { throw it }
    }

    external fun init(modelPath: String, dvectorPath: String): Boolean

    external fun processFrame(audioFrame: FloatArray): FloatArray?

    external fun release()

    init {
        ensureLoaded()
    }
}
