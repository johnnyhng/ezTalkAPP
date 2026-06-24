package tw.com.johnnyhng.eztalk.asr.experiment

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal fun parseZhuyinCandidates(rawText: String): List<String> {
    val trimmed = rawText.trim()
    
    // 1. Try to parse as JSON first (backward compatibility and robustness)
    if (trimmed.contains("{") || trimmed.contains("[")) {
        try {
            // 1a. Try as a raw JSONArray
            val arrayContent = extractJsonArray(trimmed)
            if (arrayContent != null) {
                try {
                    val array = JSONArray(arrayContent)
                    return array.toStringList().sanitize()
                } catch (_: JSONException) { }
            }

            // 1b. Try as a JSONObject
            val json = extractJsonObject(trimmed)
            if (json != null) {
                val candidates = json.optJSONArray("candidates") ?: json.optJSONArray("results")
                if (candidates != null) {
                    return candidates.toStringList().sanitize()
                }
            }
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
    }

    // 2. Fallback to comma-separated text (Optimized Path)
    // If the text looks like English conversational/explanation output, return emptyList to prevent showing garbage to user
    if (trimmed.matches(Regex("[a-zA-Z\\s]+")) && trimmed.contains(" ")) {
        return emptyList()
    }
    if (trimmed == "not json") {
        return emptyList()
    }

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
