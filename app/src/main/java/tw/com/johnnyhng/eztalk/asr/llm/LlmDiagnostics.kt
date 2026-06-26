package tw.com.johnnyhng.eztalk.asr.llm

import android.util.Log

internal const val LLM_LOG_TAG = "ezTalk-LLM"

internal fun safeLogInfo(tag: String, message: String) {
    try {
        Log.i(tag, message)
    } catch (_: RuntimeException) {
        // android.util.Log is not mocked in local JVM unit tests.
    }
}

internal fun safeLogDebug(tag: String, message: String) {
    try {
        Log.d(tag, message)
    } catch (_: RuntimeException) {
        // android.util.Log is not mocked in local JVM unit tests.
    }
}

internal fun safeLogWarning(tag: String, message: String, throwable: Throwable? = null) {
    try {
        if (throwable == null) {
            Log.w(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
    } catch (_: RuntimeException) {
        // android.util.Log is not mocked in local JVM unit tests.
    }
}
