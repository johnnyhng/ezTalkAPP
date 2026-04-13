package tw.com.johnnyhng.eztalk.asr.speaker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tw.com.johnnyhng.eztalk.asr.audio.SpeechOutputDriver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeakerPlaybackControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun playDocumentCompletesAcrossSegments() {
        val driver = FakeSpeechOutputDriver()
        val controller = SpeakerPlaybackController(
            context = context,
            onStateChanged = {},
            speechControllerFactory = { _, onReadyChanged ->
                driver.onReadyChanged = onReadyChanged
                driver
            }
        )
        controller.initialize()

        val result = controller.playDocument(
            SpeakerDocumentUi(
                id = "doc-1",
                displayName = "doc-1",
                previewText = "",
                fullText = "第一句。第二句"
            )
        )

        assertEquals(SpeakerPlaybackResult.STARTED, result)
        assertEquals(listOf("第一句"), driver.spokenTexts)

        driver.completeCurrent()
        assertEquals(listOf("第一句", "第二句"), driver.spokenTexts)

        driver.completeCurrent()
        val state = controller.currentState()
        assertNull(state.currentPlayingDocumentId)
        assertNull(state.playbackDocumentId)
        assertEquals("doc-1", state.completedDocumentId)
        assertEquals(1, state.completionVersion)
    }

    @Test
    fun pauseKeepsPlaybackPositionForResume() {
        val driver = FakeSpeechOutputDriver()
        val controller = SpeakerPlaybackController(
            context = context,
            onStateChanged = {},
            speechControllerFactory = { _, onReadyChanged ->
                driver.onReadyChanged = onReadyChanged
                driver
            }
        )
        controller.initialize()

        controller.playDocument(
            SpeakerDocumentUi(
                id = "doc-2",
                displayName = "doc-2",
                previewText = "",
                fullText = "甲。乙"
            )
        )

        controller.pause("doc-2")
        var state = controller.currentState()
        assertTrue(state.isPlaybackPaused)
        assertEquals(0, state.playbackSegmentIndex)
        assertNull(state.currentPlayingDocumentId)
        assertTrue(driver.stopCalled)

        controller.playDocument(
            SpeakerDocumentUi(
                id = "doc-2",
                displayName = "doc-2",
                previewText = "",
                fullText = "甲。乙"
            )
        )

        state = controller.currentState()
        assertFalse(state.isPlaybackPaused)
        assertEquals("doc-2", state.playbackDocumentId)
        assertEquals(listOf("甲", "甲"), driver.spokenTexts)
    }

    @Test
    fun playDocumentReturnsNotReadyWhenSpeechDriverNotReady() {
        val controller = SpeakerPlaybackController(
            context = context,
            onStateChanged = {},
            speechControllerFactory = { _, _ -> FakeSpeechOutputDriver(readyOnInitialize = false) }
        )

        controller.initialize()

        val result = controller.playDocument(
            SpeakerDocumentUi(
                id = "doc-3",
                displayName = "doc-3",
                previewText = "",
                fullText = "內容"
            )
        )

        assertEquals(SpeakerPlaybackResult.NOT_READY, result)
    }
}

private class FakeSpeechOutputDriver(
    private val readyOnInitialize: Boolean = true
) : SpeechOutputDriver {
    var onReadyChanged: ((Boolean) -> Unit)? = null
    val spokenTexts = mutableListOf<String>()
    var stopCalled = false

    private var currentOnStart: (() -> Unit)? = null
    private var currentOnDone: (() -> Unit)? = null
    private var currentOnError: (() -> Unit)? = null

    override fun initialize() {
        onReadyChanged?.invoke(readyOnInitialize)
    }

    override fun speak(
        text: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: (() -> Unit)?
    ): Boolean {
        spokenTexts += text
        currentOnStart = onStart
        currentOnDone = onDone
        currentOnError = onError
        onStart?.invoke()
        return true
    }

    override fun stop() {
        stopCalled = true
    }

    override fun dispose() = Unit

    fun completeCurrent() {
        currentOnDone?.invoke()
    }
}
