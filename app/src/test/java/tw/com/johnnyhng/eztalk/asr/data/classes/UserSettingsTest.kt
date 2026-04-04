package tw.com.johnnyhng.eztalk.asr.data.classes

import org.junit.Assert.assertEquals
import org.junit.Test

class UserSettingsTest {
    @Test
    fun effectiveRecognitionUrlAlwaysResolvesFromBackendUrl() {
        val settings = UserSettings(
            backendUrl = "https://backend.example.com",
            recognitionUrl = "https://recognition.example.com/process_audio"
        )

        assertEquals(
            "https://backend.example.com/process_audio",
            settings.effectiveRecognitionUrl
        )
    }

    @Test
    fun effectiveRecognitionUrlBuildsFromBackendUrlAndRemovesTrailingSlash() {
        val settings = UserSettings(
            backendUrl = "https://backend.example.com/"
        )

        assertEquals(
            "https://backend.example.com/process_audio",
            settings.effectiveRecognitionUrl
        )
    }

    @Test
    fun effectiveRecognitionUrlReturnsEmptyWhenBackendUrlIsBlank() {
        val settings = UserSettings(
            backendUrl = "   ",
            recognitionUrl = ""
        )

        assertEquals("", settings.effectiveRecognitionUrl)
    }
}
