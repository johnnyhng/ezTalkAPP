package tw.com.johnnyhng.eztalk.asr.data.classes

import org.junit.Assert.assertEquals
import org.junit.Test

class UserSettingsTest {
    @Test
    fun effectiveRecognitionUrlUsesExplicitRecognitionUrlWhenPresent() {
        val settings = UserSettings(
            backendUrl = "https://backend.example.com",
            recognitionUrl = "https://recognition.example.com/process_audio"
        )

        assertEquals(
            "https://recognition.example.com/process_audio",
            settings.effectiveRecognitionUrl
        )
    }

    @Test
    fun effectiveRecognitionUrlBuildsFromBackendUrlAndRemovesTrailingSlash() {
        val settings = UserSettings(
            backendUrl = "https://backend.example.com/"
        )

        assertEquals(
            "https://backend.example.com/api/process_audio",
            settings.effectiveRecognitionUrl
        )
    }

    @Test
    fun effectiveRecognitionUrlReturnsEmptyWhenBackendUrlIsBlankAndRecognitionUrlMissing() {
        val settings = UserSettings(
            backendUrl = "   ",
            recognitionUrl = ""
        )

        assertEquals("", settings.effectiveRecognitionUrl)
    }
}
