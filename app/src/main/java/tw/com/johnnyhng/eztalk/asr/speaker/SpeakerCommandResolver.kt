package tw.com.johnnyhng.eztalk.asr.speaker

internal sealed interface SpeakerContentCommand {
    data object Play : SpeakerContentCommand
    data object Pause : SpeakerContentCommand
    data object Stop : SpeakerContentCommand
    data class PlayLine(val lineIndex: Int) : SpeakerContentCommand
}

internal fun resolveSpeakerContentCommand(
    commandText: String,
    availableLines: List<String>
): SpeakerContentCommand? {
    val normalized = commandText.trim().replace("\\s+".toRegex(), "")
    if (normalized.isBlank()) return null

    val requestedLineIndex = resolveRequestedLineIndex(normalized)
    requestedLineIndex?.let { requestedIndex ->
        val lineIndex = requestedIndex - 1
        if (lineIndex in availableLines.indices && availableLines[lineIndex].isNotBlank()) {
            return SpeakerContentCommand.PlayLine(lineIndex)
        }
        return null
    }

    if (normalized.contains("行")) {
        return null
    }

    return when {
        normalized.contains("暫停") -> SpeakerContentCommand.Pause
        normalized.contains("停止") || normalized.contains("停播") -> SpeakerContentCommand.Stop
        normalized.contains("播放") || normalized.contains("繼續") -> SpeakerContentCommand.Play
        else -> null
    }
}

private fun resolveRequestedLineIndex(text: String): Int? {
    val explicitDigitMatch = Regex("第(\\d+)行").find(text)
    if (explicitDigitMatch != null) {
        return explicitDigitMatch.groupValues[1].toIntOrNull()
    }

    val explicitChineseMatch = Regex("第([零一二兩三四五六七八九十百]+)行").find(text)
    if (explicitChineseMatch != null) {
        return parseSimpleChineseNumber(explicitChineseMatch.groupValues[1])
    }

    val bareDigitMatch = Regex("(\\d+)行").find(text)
    if (bareDigitMatch != null) {
        return bareDigitMatch.groupValues[1].toIntOrNull()
    }

    val bareChineseMatch = Regex("([零一二兩三四五六七八九十百]+)行").find(text)
    if (bareChineseMatch != null) {
        return parseSimpleChineseNumber(bareChineseMatch.groupValues[1])
    }

    return null
}

private fun parseSimpleChineseNumber(text: String): Int? {
    if (text.isBlank()) return null
    val digitMap = mapOf(
        '零' to 0,
        '一' to 1,
        '二' to 2,
        '兩' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9
    )

    if (text == "十") return 10
    if ('百' in text) {
        val parts = text.split('百')
        val hundreds = parts.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
            digitMap[it.singleOrNull()] ?: return null
        } ?: 1
        val remainder = parts.getOrNull(1).orEmpty()
        val tail = if (remainder.isBlank()) 0 else parseSimpleChineseNumber(remainder) ?: return null
        return hundreds * 100 + tail
    }
    if ('十' in text) {
        val parts = text.split('十')
        val tens = parts.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
            digitMap[it.singleOrNull()] ?: return null
        } ?: 1
        val ones = parts.getOrNull(1).orEmpty().takeIf { it.isNotBlank() }?.let {
            digitMap[it.singleOrNull()] ?: return null
        } ?: 0
        return tens * 10 + ones
    }

    return text
        .map { digitMap[it] ?: return null }
        .fold(0) { acc, value -> acc * 10 + value }
}
