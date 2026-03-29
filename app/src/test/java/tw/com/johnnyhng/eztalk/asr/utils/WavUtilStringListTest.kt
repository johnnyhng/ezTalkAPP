package tw.com.johnnyhng.eztalk.asr.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavUtilStringListTest {
    @Test
    fun optStringListReturnsEmptyListWhenKeyIsMissing() {
        val json = JSONObject()

        val result = json.optStringList("local_candidates")

        assertTrue(result.isEmpty())
    }

    @Test
    fun optStringListReturnsEmptyListWhenArrayIsEmpty() {
        val json = JSONObject().apply {
            put("remote_candidates", JSONArray())
        }

        val result = json.optStringList("remote_candidates")

        assertTrue(result.isEmpty())
    }

    @Test
    fun optStringListFiltersOutBlankItemsAndPreservesOrder() {
        val json = JSONObject().apply {
            put(
                "local_candidates",
                JSONArray(
                    listOf(
                        "first",
                        "",
                        "   ",
                        "second",
                        "third"
                    )
                )
            )
        }

        val result = json.optStringList("local_candidates")

        assertEquals(listOf("first", "second", "third"), result)
    }
}
