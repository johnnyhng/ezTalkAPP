package tw.com.johnnyhng.eztalk.asr.data.classes

import org.junit.Assert.assertEquals
import org.junit.Test

class UserSettingsTest {
    @Test
    fun defaultValuesDefineCanonicalSettingsDefaults() {
        val settings = UserSettings()

        assertEquals("default_user", settings.userId)
        assertEquals(5000f, settings.lingerMs)
        assertEquals(500f, settings.partialIntervalMs)
        assertEquals(false, settings.saveVadSegmentsOnly)
        assertEquals(false, settings.inlineEdit)
        assertEquals("https://120.126.151.159:56432/api/v2", settings.backendUrl)
        assertEquals(true, settings.enableTtsFeedback)
        assertEquals("", settings.selectedModelName)
    }

    @Test
    fun effectiveRecognitionUrlAlwaysResolvesFromBackendUrl() {
        val settings = UserSettings(
            backendUrl = "https://backend.example.com"
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
            backendUrl = "   "
        )

        assertEquals("", settings.effectiveRecognitionUrl)
    }
}
