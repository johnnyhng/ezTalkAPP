package tw.com.johnnyhng.eztalk.asr.experiment

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal fun parseZhuyinCandidates(rawText: String): List<String> {
    val trimmed = rawText.trim()
    
    // 1. Try to parse as JSON first (backward compatibility and robustness)
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        try {
            // 1a. Try as a raw JSONArray
            try {
                val array = JSONArray(extractJsonArray(trimmed) ?: trimmed)
                return array.toStringList().sanitize()
            } catch (_: JSONException) { }

            // 1b. Try as a JSONObject
            val json = extractJsonObject(trimmed) ?: return emptyList()
            val candidates = json.optJSONArray("candidates") ?: json.optJSONArray("results") ?: return emptyList()
            return candidates.toStringList().sanitize()
        } catch (_: Exception) {
            // If JSON parsing fails despite looking like JSON, fall through to text parsing
        }
    }

    // 2. Fallback to comma-separated text (Optimized Path)
    // Support both full-width and half-width commas
    return trimmed.split(Regex("[,，]")).sanitize()
}

private fun List<String>.sanitize(): List<String> {
    return this.map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun JSONArray.toStringList(): List<String> {
    return buildList {
        for (index in 0 until length()) {
            val obj = optJSONObject(index)
            if (obj != null) {
                // It's an object like {"text": "..."} or {"candidate": "..."}
                val text = obj.optString("text").takeIf { it.isNotBlank() }
                    ?: obj.optString("candidate").takeIf { it.isNotBlank() }
                    ?: obj.optString("value").takeIf { it.isNotBlank() }
                if (text != null) add(text)
            } else {
                // It's a raw string or other primitive
                val value = optString(index).takeIf { it.isNotBlank() } ?: continue
                add(value)
            }
        }
    }
}

private fun extractJsonArray(rawText: String): String? {
    val start = rawText.indexOf('[')
    val end = rawText.lastIndexOf(']')
    if (start == -1 || end <= start) return null
    return rawText.substring(start, end + 1)
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
