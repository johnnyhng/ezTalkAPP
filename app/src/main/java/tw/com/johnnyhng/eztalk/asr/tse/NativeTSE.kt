package tw.com.johnnyhng.eztalk.asr.tse

/**
 * Stable app-facing wrapper around the JNI-bound NativeTSE class.
 */
class NativeTSE {
    private val delegate = com.example.tse.NativeTSE()

    fun init(modelPath: String, dvectorPath: String): Boolean {
        return delegate.init(modelPath, dvectorPath)
    }

    fun processFrame(audioFrame: FloatArray): FloatArray? {
        return delegate.processFrame(audioFrame)
    }

    fun release() {
        delegate.release()
    }
}
