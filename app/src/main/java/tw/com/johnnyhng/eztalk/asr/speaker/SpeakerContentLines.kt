package tw.com.johnnyhng.eztalk.asr.speaker

private val SPEAKER_CONTENT_LINE_SPLIT_REGEX = Regex("[\\n。.]")

internal fun buildSpeakerContentLines(text: String): List<String> {
    return text
        .replace("\r\n", "\n")
        .split(SPEAKER_CONTENT_LINE_SPLIT_REGEX)
        .map(String::trim)
        .filter(String::isNotBlank)
}
