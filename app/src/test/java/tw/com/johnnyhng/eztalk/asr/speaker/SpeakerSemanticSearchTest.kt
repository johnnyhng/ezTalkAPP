package tw.com.johnnyhng.eztalk.asr.speaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import tw.com.johnnyhng.eztalk.asr.screens.SpeakerDocumentUi

class SpeakerSemanticSearchTest {
    @Test
    fun indexerBuildsSlidingChunksFromDocumentLines() {
        val indexer = SpeakerSemanticIndexer()
        val document = SpeakerDocumentUi(
            id = "doc-1",
            displayName = "demo.txt",
            previewText = "",
            fullText = "第一行\n第二行\n第三行"
        )

        val chunks = indexer.indexDocument(document)

        assertEquals(3, chunks.size)
        assertEquals(0, chunks[0].lineStart)
        assertEquals(2, chunks[0].lineEnd)
        assertEquals("第一行\n第二行\n第三行", chunks[0].text)
        assertEquals(1, chunks[1].lineStart)
        assertEquals(2, chunks[1].lineEnd)
    }

    @Test
    fun searchReturnsBestLexicalMatchWithoutEmbeddings() {
        val search = SpeakerSemanticSearch(
            embeddingEngine = SpeakerEmbeddingEngine { text ->
                when (text) {
                    "校園回憶" -> floatArrayOf(1f, 0f)
                    "想起去年秋天的校園生活" -> floatArrayOf(0.95f, 0.05f)
                    "今天晚上要去超市買菜" -> floatArrayOf(0f, 1f)
                    else -> floatArrayOf()
                }
            },
            config = SpeakerSemanticSearchConfig(minimumScoreThreshold = 0.1f)
        )
        val chunks = listOf(
            SpeakerIndexedChunk(
                documentId = "doc-1",
                lineStart = 0,
                lineEnd = 0,
                text = "想起去年秋天的校園生活",
                embedding = floatArrayOf(0.95f, 0.05f)
            ),
            SpeakerIndexedChunk(
                documentId = "doc-1",
                lineStart = 1,
                lineEnd = 1,
                text = "今天晚上要去超市買菜",
                embedding = floatArrayOf(0f, 1f)
            )
        )

        val result = search.search("校園回憶", chunks)

        assertNotNull(result)
        assertEquals(0, result?.lineStart)
        assertEquals("想起去年秋天的校園生活", result?.matchedText)
    }

    @Test
    fun searchReturnsNullWhenScoreBelowThreshold() {
        val search = SpeakerSemanticSearch(
            config = SpeakerSemanticSearchConfig(minimumScoreThreshold = 0.95f)
        )
        val chunks = listOf(
            SpeakerIndexedChunk(
                documentId = "doc-1",
                lineStart = 0,
                lineEnd = 0,
                text = "完全不同的句子"
            )
        )

        assertNull(search.search("校園回憶", chunks))
    }
}
