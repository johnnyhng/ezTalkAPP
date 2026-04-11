package tw.com.johnnyhng.eztalk.asr.speaker

import android.content.Context
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class SpeakerContentAsrActionsTest {

    @Test
    fun handleSpeakerContentCommand_checksAllUtteranceVariantsBeforeSemanticFallback() {
        val context = RuntimeEnvironment.getApplication().applicationContext as Context
        val document = SpeakerDocumentUi(
            id = "doc-1",
            displayName = "demo.txt",
            previewText = "",
            fullText = "第一行\n第二行"
        )
        val utterance = SpeakerAsrUtteranceBundle(
            primaryText = "我要播一下",
            variants = listOf("我要播一下", "播放"),
            finalTextVersion = 1
        )
        var played = false

        val handled = handleSpeakerContentCommand(
            context = context,
            utterance = utterance,
            document = document,
            contentLines = listOf("第一行", "第二行"),
            isSelectedDocumentPlaying = false,
            isSelectedDocumentPaused = false,
            resetContentSemanticUi = {},
            pauseDocument = {},
            stopPlayback = {},
            playDocumentWithAsrStop = {
                played = true
                SpeakerPlaybackResult.STARTED
            },
            playLineWithAsrStop = { _, _, _ -> SpeakerPlaybackResult.STARTED }
        )

        assertTrue(handled)
        assertTrue(played)
    }
}
