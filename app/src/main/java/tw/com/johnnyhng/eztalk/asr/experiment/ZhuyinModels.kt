package tw.com.johnnyhng.eztalk.asr.experiment

internal data class ZhuyinKeyGroup(
    val label: String,
    val rows: List<String>
)

internal data class ZhuyinEmotion(
    val emoji: String,
    val prompt: String,
    val label: String
)

internal val zhuyinSingleRowKeyGroups: List<ZhuyinKeyGroup> = listOf(
    ZhuyinKeyGroup(label = "ㄅ", rows = listOf("ㄅㄆㄇㄈ")),
    ZhuyinKeyGroup(label = "ㄉ", rows = listOf("ㄉㄊㄋㄌ")),
    ZhuyinKeyGroup(label = "ㄍ", rows = listOf("ㄍㄎㄏ")),
    ZhuyinKeyGroup(label = "ㄐ", rows = listOf("ㄐㄑㄒ")),
    ZhuyinKeyGroup(label = "ㄓ", rows = listOf("ㄓㄔㄕㄖ")),
    ZhuyinKeyGroup(label = "ㄗ", rows = listOf("ㄗㄘㄙ")),
    ZhuyinKeyGroup(label = "ㄧ", rows = listOf("ㄧㄨㄩ")),
    ZhuyinKeyGroup(label = "ㄚ", rows = listOf("ㄚㄛㄜㄝ")),
    ZhuyinKeyGroup(label = "ㄞ", rows = listOf("ㄞㄟㄠㄡ")),
    ZhuyinKeyGroup(label = "ㄢ", rows = listOf("ㄢㄣㄤㄥㄦ")),
    ZhuyinKeyGroup(label = "聲詞", rows = listOf("␣˙ˊˇˋ"))
)

internal val traditionalChineseInitialPhrases: List<String> = listOf(
    "你",
    "我",
    "他",
    "她",
    "好",
    "今天",
    "昨天",
    "明天",
    "謝謝"
)

internal val traditionalChineseEmotions: List<ZhuyinEmotion> = listOf(
    ZhuyinEmotion(emoji = "💬", prompt = "陳述", label = "普通"),
    ZhuyinEmotion(emoji = "❓", prompt = "疑問", label = "提問"),
    ZhuyinEmotion(emoji = "🙏", prompt = "請求", label = "拜託"),
    ZhuyinEmotion(emoji = "🚫", prompt = "否定", label = "否定")
)
