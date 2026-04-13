package tw.com.johnnyhng.eztalk.asr.audio

import android.media.AudioAttributes
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioIOManagerTest {
    @Test
    fun resolvePlaybackCapturePolicyReturnsNullBeforeAndroidQ() {
        val result = resolvePlaybackCapturePolicy(
            allowAppAudioCapture = true,
            sdkInt = Build.VERSION_CODES.P
        )

        assertNull(result)
    }

    @Test
    fun resolvePlaybackCapturePolicyAllowsCaptureWhenEnabled() {
        val result = resolvePlaybackCapturePolicy(
            allowAppAudioCapture = true,
            sdkInt = Build.VERSION_CODES.Q
        )

        assertEquals(AudioAttributes.ALLOW_CAPTURE_BY_ALL, result)
    }

    @Test
    fun resolvePlaybackCapturePolicyRestrictsCaptureWhenDisabled() {
        val result = resolvePlaybackCapturePolicy(
            allowAppAudioCapture = false,
            sdkInt = Build.VERSION_CODES.Q
        )

        assertEquals(AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM, result)
    }
}
