package tw.com.johnnyhng.eztalk.asr.experiment

private val trailingZhuyinRegex = Regex("[\\u3100-\\u312F\\u02CA\\u02C7\\u02CB\\u02D9]+$")

internal fun stripTrailingZhuyin(text: String): String {
    return text.replace(trailingZhuyinRegex, "")
}

internal fun appendZhuyinCandidate(text: String, candidate: String): String {
    val strippedText = stripTrailingZhuyin(text)
    return if (candidate.startsWith("-")) {
        strippedText + candidate.drop(1)
    } else {
        strippedText + candidate
    }
}

internal fun segmentTraditionalChinese(text: String): List<String> {
    return text.toList().map { it.toString() }
}

internal fun joinTraditionalChinese(segments: List<String>): String {
    return segments.joinToString(separator = "")
}
