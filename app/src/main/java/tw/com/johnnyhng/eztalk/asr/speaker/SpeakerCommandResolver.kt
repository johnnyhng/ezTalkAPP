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
    val normalized = normalizeSpeakerCommandText(commandText)
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
        matchesAnyCommandKeyword(normalized, PAUSE_COMMAND_KEYWORDS) -> SpeakerContentCommand.Pause
        matchesAnyCommandKeyword(normalized, STOP_COMMAND_KEYWORDS) -> SpeakerContentCommand.Stop
        matchesAnyCommandKeyword(normalized, PLAY_COMMAND_KEYWORDS) -> SpeakerContentCommand.Play
        else -> null
    }
}

private fun resolveRequestedLineIndex(text: String): Int? {
    val explicitDigitMatch = LINE_REQUEST_DIGIT_REGEX.find(text)
    if (explicitDigitMatch != null) {
        return explicitDigitMatch.groupValues[1].toIntOrNull()
    }

    val explicitChineseMatch = LINE_REQUEST_CHINESE_REGEX.find(text)
    if (explicitChineseMatch != null) {
        return parseSimpleChineseNumber(explicitChineseMatch.groupValues[1])
    }

    val bareDigitMatch = BARE_LINE_REQUEST_DIGIT_REGEX.find(text)
    if (bareDigitMatch != null) {
        return bareDigitMatch.groupValues[1].toIntOrNull()
    }

    val bareChineseMatch = BARE_LINE_REQUEST_CHINESE_REGEX.find(text)
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

private fun normalizeSpeakerCommandText(text: String): String {
    return text
        .trim()
        .replace("\\s+".toRegex(), "")
        .replace("[，。、「」？！：；,.!?]".toRegex(), "")
        .replace('撥', '播')
        .replace('拨', '播')
        .replace('續', '续')
        .replace('繼', '继')
        .replace('暫', '暂')
        .let { normalized ->
            normalized.map { char ->
                LINE_SUFFIX_NORMALIZATION[char] ?: char
            }.joinToString("")
        }
}

private fun matchesAnyCommandKeyword(
    normalizedText: String,
    keywords: Set<String>
): Boolean {
    return keywords.any(normalizedText::contains)
}

private val PLAY_COMMAND_KEYWORDS = setOf(
    "播放",
    "播",
    "继续",
    "繼續",
    "續播",
    "开始播放",
    "開始播放",
    "开始",
    "開始"
)

private val PAUSE_COMMAND_KEYWORDS = setOf(
    "暂停",
    "暫停",
    "暂停播放",
    "暫停播放"
)

private val STOP_COMMAND_KEYWORDS = setOf(
    "停止",
    "停播",
    "停止播放",
    "停下来",
    "停下來"
)

private val LINE_SUFFIX_NORMALIZATION = mapOf(
    '航' to '行',
    '杭' to '行',
    '型' to '行',
    '項' to '行',
    '项' to '行',
    '橫' to '行',
    '横' to '行',
    '号' to '行',
    '號' to '行'
)
private val LINE_REQUEST_DIGIT_REGEX = Regex("第(\\d+)行")
private val LINE_REQUEST_CHINESE_REGEX = Regex("第([零一二兩三四五六七八九十百]+)行")
private val BARE_LINE_REQUEST_DIGIT_REGEX = Regex("(\\d+)行")
private val BARE_LINE_REQUEST_CHINESE_REGEX = Regex("([零一二兩三四五六七八九十百]+)行")
