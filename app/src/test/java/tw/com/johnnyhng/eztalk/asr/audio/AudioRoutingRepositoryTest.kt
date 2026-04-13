package tw.com.johnnyhng.eztalk.asr.audio

import android.media.AudioDeviceInfo
import android.media.AudioRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRoutingRepositoryTest {
    @Test
    fun buildPreferredInputRoutingMessageHandlesSystemDefault() {
        val message = buildPreferredInputRoutingMessage(
            selectedInput = null,
            inputApplied = false,
            apiSupportsPreferredDevice = true
        )

        assertEquals("Using system default microphone route", message)
    }

    @Test
    fun buildPreferredInputRoutingMessageReportsMissingDevice() {
        val message = buildPreferredInputRoutingMessage(
            selectedInput = null,
            inputApplied = false,
            apiSupportsPreferredDevice = false
        )

        assertEquals("Preferred microphone routing is unavailable on this Android version", message)
    }

    @Test
    fun buildPreferredInputRoutingMessageReportsApplyResult() {
        val device = AudioRouteDeviceUi(
            id = 7,
            productName = "USB Mic",
            type = AudioDeviceInfo.TYPE_USB_DEVICE,
            typeLabel = "USB audio",
            isInput = true,
            isOutput = false,
            isConnected = true,
            isCommunicationDeviceCapable = false
        )

        val applied = buildPreferredInputRoutingMessage(
            selectedInput = device,
            inputApplied = true,
            apiSupportsPreferredDevice = true
        )
        val rejected = buildPreferredInputRoutingMessage(
            selectedInput = device,
            inputApplied = false,
            apiSupportsPreferredDevice = true
        )

        assertTrue(applied.contains("USB Mic"))
        assertTrue(applied.contains("requested"))
        assertTrue(rejected.contains("rejected"))
    }
}
