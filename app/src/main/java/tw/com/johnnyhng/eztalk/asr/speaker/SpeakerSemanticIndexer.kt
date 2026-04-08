package tw.com.johnnyhng.eztalk.asr.speaker

import tw.com.johnnyhng.eztalk.asr.ui.speaker.SpeakerDocumentUi

internal fun interface SpeakerEmbeddingEngine {
    fun embed(text: String): FloatArray
}

internal class SpeakerSemanticIndexer(
    private val embeddingEngine: SpeakerEmbeddingEngine? = null,
    private val config: SpeakerSemanticSearchConfig = SpeakerSemanticSearchConfig()
) {
    fun indexDocument(document: SpeakerDocumentUi): List<SpeakerIndexedChunk> {
        val lines = document.fullText
            .replace("\r\n", "\n")
            .split('\n')

        return buildChunkWindows(lines, config.chunkLineWindow).map { window ->
            val chunkText = window.lines
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")

            SpeakerIndexedChunk(
                documentId = document.id,
                lineStart = window.lineStart,
                lineEnd = window.lineEnd,
                text = chunkText,
                embedding = embeddingEngine?.embed(chunkText).orEmpty()
            )
        }
    }

    private fun buildChunkWindows(
        lines: List<String>,
        chunkLineWindow: Int
    ): List<ChunkWindow> {
        if (lines.isEmpty()) return emptyList()

        val windows = mutableListOf<ChunkWindow>()
        val windowSize = chunkLineWindow.coerceAtLeast(1)
        for (start in lines.indices) {
            val endExclusive = minOf(start + windowSize, lines.size)
            val slice = lines.subList(start, endExclusive)
            if (slice.any { it.isNotBlank() }) {
                windows += ChunkWindow(
                    lineStart = start,
                    lineEnd = endExclusive - 1,
                    lines = slice
                )
            }
        }
        return windows
    }

    private data class ChunkWindow(
        val lineStart: Int,
        val lineEnd: Int,
        val lines: List<String>
    )
}

private fun FloatArray?.orEmpty(): FloatArray = this ?: floatArrayOf()
