package tw.com.johnnyhng.eztalk.asr.llm

internal enum class LocalGemmaBackend(
    val storageValue: String
) {
    AUTO("auto"),
    NPU("npu"),
    GPU("gpu"),
    CPU("cpu");

    companion object {
        fun fromStorageValue(value: String): LocalGemmaBackend {
            return entries.firstOrNull { it.storageValue == value } ?: AUTO
        }
    }
}
