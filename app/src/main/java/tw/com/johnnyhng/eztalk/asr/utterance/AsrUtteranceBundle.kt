package tw.com.johnnyhng.eztalk.asr.utterance

internal data class AsrUtteranceBundle(
    val primaryText: String,
    val variants: List<String>,
    val version: Int,
    val boundaryReason: String? = null
)
