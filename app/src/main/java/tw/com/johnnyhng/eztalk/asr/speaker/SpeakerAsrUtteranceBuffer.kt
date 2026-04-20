package tw.com.johnnyhng.eztalk.asr.speaker

import tw.com.johnnyhng.eztalk.asr.utterance.AsrUtteranceVariantBuffer

internal data class SpeakerAsrUtteranceBundle(
    val primaryText: String,
    val variants: List<String>,
    val finalTextVersion: Int
)

internal class SpeakerAsrUtteranceBuffer {
    private val delegate = AsrUtteranceVariantBuffer()

    fun reset() {
        delegate.reset()
    }

    fun add(text: String) {
        delegate.add(text)
    }

    fun variants(): List<String> {
        return delegate.variants()
    }

    fun build(finalTextVersion: Int): SpeakerAsrUtteranceBundle? {
        return delegate.build(version = finalTextVersion)?.let { bundle ->
            SpeakerAsrUtteranceBundle(
                primaryText = bundle.primaryText,
                variants = bundle.variants,
                finalTextVersion = bundle.version
            )
        }
    }
}
