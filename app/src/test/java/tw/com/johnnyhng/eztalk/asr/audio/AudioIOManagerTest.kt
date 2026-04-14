package tw.com.johnnyhng.eztalk.asr.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioIOManagerTest {
    @Test
    fun resolvePreferredAudioSourceUsesVoiceCommunicationForBluetoothSco() {
        val result = resolvePreferredAudioSource(
            selectedInputType = AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            defaultAudioSource = MediaRecorder.AudioSource.MIC
        )

        assertEquals(MediaRecorder.AudioSource.VOICE_COMMUNICATION, result)
    }

    @Test
    fun resolvePreferredAudioSourceKeepsDefaultForBuiltInMic() {
        val result = resolvePreferredAudioSource(
            selectedInputType = AudioDeviceInfo.TYPE_BUILTIN_MIC,
            defaultAudioSource = MediaRecorder.AudioSource.MIC
        )

        assertEquals(MediaRecorder.AudioSource.MIC, result)
    }

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
