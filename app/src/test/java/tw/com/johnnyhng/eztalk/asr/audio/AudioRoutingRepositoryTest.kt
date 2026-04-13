package tw.com.johnnyhng.eztalk.asr.audio

import android.media.AudioDeviceInfo
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

    @Test
    fun buildPreferredOutputRoutingMessageHandlesSystemDefault() {
        val message = buildPreferredOutputRoutingMessage(
            selectedOutput = null,
            outputApplied = false,
            apiSupportsPreferredDevice = true
        )

        assertEquals("Using system default playback route", message)
    }

    @Test
    fun buildPreferredOutputRoutingMessageReportsUnsupportedApi() {
        val message = buildPreferredOutputRoutingMessage(
            selectedOutput = null,
            outputApplied = false,
            apiSupportsPreferredDevice = false
        )

        assertEquals("Preferred playback routing is unavailable on this Android version", message)
    }

    @Test
    fun buildPreferredOutputRoutingMessageReportsApplyResult() {
        val device = AudioRouteDeviceUi(
            id = 9,
            productName = "USB DAC",
            type = AudioDeviceInfo.TYPE_USB_DEVICE,
            typeLabel = "USB audio",
            isInput = false,
            isOutput = true,
            isConnected = true,
            isCommunicationDeviceCapable = true
        )

        val applied = buildPreferredOutputRoutingMessage(
            selectedOutput = device,
            outputApplied = true,
            apiSupportsPreferredDevice = true
        )
        val rejected = buildPreferredOutputRoutingMessage(
            selectedOutput = device,
            outputApplied = false,
            apiSupportsPreferredDevice = true
        )

        assertTrue(applied.contains("USB DAC"))
        assertTrue(applied.contains("requested"))
        assertTrue(rejected.contains("rejected"))
    }
}
