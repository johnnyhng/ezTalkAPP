package tw.com.johnnyhng.eztalk.asr.utterance

internal fun interface AsrUtteranceTextNormalizer {
    fun normalize(text: String): String
}

internal object DefaultAsrUtteranceTextNormalizer : AsrUtteranceTextNormalizer {
    override fun normalize(text: String): String {
        return text
            .trim()
            .replace("\\s+".toRegex(), "")
            .replace("[，。、「」？！：；,.!?]".toRegex(), "")
            .lowercase()
    }
}
