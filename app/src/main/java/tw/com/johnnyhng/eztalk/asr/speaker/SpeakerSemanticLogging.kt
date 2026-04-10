package tw.com.johnnyhng.eztalk.asr.speaker

internal fun FloatArray.previewForLog(maxSize: Int = 8): String {
    if (isEmpty()) return "[]"
    return take(maxSize).joinToString(
        prefix = "[",
        postfix = if (size > maxSize) ", ...]" else "]"
    ) { value ->
        "%.4f".format(value)
    }
}

internal fun List<SpeakerSearchResult>.formatTop3CosineForLog(): String {
    if (isEmpty()) return "[]"
    return take(3).joinToString(
        prefix = "[",
        postfix = "]"
    ) { result ->
        "{cos=${"%.4f".format(result.semanticScore)}, hybrid=${"%.4f".format(result.finalScore)}, lines=${result.lineStart}-${result.lineEnd}, text=${result.matchedText.oneLineForLog()}}"
    }
}

internal fun String.oneLineForLog(maxLength: Int = 60): String {
    val normalized = replace('\n', ' ').trim()
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        normalized.take(maxLength) + "..."
    }
}
