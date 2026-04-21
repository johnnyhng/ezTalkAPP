package tw.com.johnnyhng.eztalk.asr.experiment

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal fun parseZhuyinCandidates(rawText: String): List<String> {
    val json = extractJsonObject(rawText) ?: return emptyList()
    val candidates = json.optJSONArray("candidates") ?: return emptyList()
    return candidates.toStringList()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun JSONArray.toStringList(): List<String> {
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index).takeIf { it.isNotBlank() } ?: continue
            add(value)
        }
    }
}

private fun extractJsonObject(rawText: String): JSONObject? {
    val trimmed = rawText.trim()
    return tryParseJsonObject(trimmed)
        ?: tryParseJsonObject(
            trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        )
        ?: extractFirstObjectCandidate(trimmed)?.let(::tryParseJsonObject)
}

private fun tryParseJsonObject(candidate: String): JSONObject? {
    if (candidate.isBlank()) return null
    return try {
        JSONObject(candidate)
    } catch (_: JSONException) {
        null
    }
}

private fun extractFirstObjectCandidate(rawText: String): String? {
    val start = rawText.indexOf('{')
    val end = rawText.lastIndexOf('}')
    if (start == -1 || end <= start) return null
    return rawText.substring(start, end + 1)
}
