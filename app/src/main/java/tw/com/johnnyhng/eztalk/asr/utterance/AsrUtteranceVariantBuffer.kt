package tw.com.johnnyhng.eztalk.asr.utterance

internal class AsrUtteranceVariantBuffer(
    private val textNormalizer: AsrUtteranceTextNormalizer = DefaultAsrUtteranceTextNormalizer
) {
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
        val normalized = textNormalizer.normalize(trimmed)
        if (normalized.isBlank()) return

        variantsByNormalizedText.putIfAbsent(normalized, trimmed)
    }

    fun variants(): List<String> {
        return variantsByNormalizedText.values.toList()
    }

    fun build(
        version: Int,
        boundaryReason: String? = null
    ): AsrUtteranceBundle? {
        val currentVariants = variants()
        val primaryText = latestText.ifBlank { currentVariants.firstOrNull().orEmpty() }
        if (primaryText.isBlank() || currentVariants.isEmpty()) return null

        return AsrUtteranceBundle(
            primaryText = primaryText,
            variants = currentVariants,
            version = version,
            boundaryReason = boundaryReason
        )
    }
}
