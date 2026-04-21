package tw.com.johnnyhng.eztalk.asr.experiment

import org.junit.Assert.assertEquals
import org.junit.Test

class ZhuyinCandidateParserTest {
    @Test
    fun parseZhuyinCandidatesReadsPlainJson() {
        val result = parseZhuyinCandidates("""{"candidates":["你","您","呢"]}""")

        assertEquals(listOf("你", "您", "呢"), result)
    }

    @Test
    fun parseZhuyinCandidatesReadsFencedJson() {
        val result = parseZhuyinCandidates(
            """
            ```json
            {"candidates":["我想休息","我想喝水"]}
            ```
            """.trimIndent()
        )

        assertEquals(listOf("我想休息", "我想喝水"), result)
    }

    @Test
    fun parseZhuyinCandidatesReadsEmbeddedJsonAndDeduplicates() {
        val result = parseZhuyinCandidates(
            """Here is the JSON: {"candidates":["謝謝","謝謝","不用麻煩"]}"""
        )

        assertEquals(listOf("謝謝", "不用麻煩"), result)
    }

    @Test
    fun parseZhuyinCandidatesReturnsEmptyListForMalformedJson() {
        assertEquals(emptyList<String>(), parseZhuyinCandidates("not json"))
    }
}
