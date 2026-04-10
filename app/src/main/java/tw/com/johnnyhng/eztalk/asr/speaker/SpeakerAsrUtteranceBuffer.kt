package tw.com.johnnyhng.eztalk.asr.speaker

internal data class SpeakerAsrUtteranceBundle(
    val primaryText: String,
    val variants: List<String>,
    val finalTextVersion: Int
)

internal class SpeakerAsrUtteranceBuffer {
    private val variantsByNormalizedText = linkedMapOf<String, String>()
    private var latestText: String = ""

    fun reset() {
        variantsByNormalizedText.clear()
        latestText = ""
    }

    fun add(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        latestText = trimmed
        val normalized = normalize(trimmed)
        if (normalized.isBlank()) return

        variantsByNormalizedText.putIfAbsent(normalized, trimmed)
    }

    fun variants(): List<String> {
        return variantsByNormalizedText.values.toList()
    }

    fun build(finalTextVersion: Int): SpeakerAsrUtteranceBundle? {
        val currentVariants = variants()
        val primaryText = latestText.ifBlank { currentVariants.firstOrNull().orEmpty() }
        if (primaryText.isBlank() || currentVariants.isEmpty()) return null

        return SpeakerAsrUtteranceBundle(
            primaryText = primaryText,
            variants = currentVariants,
            finalTextVersion = finalTextVersion
        )
    }

    private fun normalize(text: String): String {
        return text
            .trim()
            .replace("\\s+".toRegex(), "")
            .replace("[，。、「」？！：；,.!?]".toRegex(), "")
            .lowercase()
    }
}
