package tw.com.johnnyhng.eztalk.asr.llm

internal enum class SpeakerLlmExecutionMode(
    val storageValue: String
) {
    AUTO_LOCAL("auto_local"),
    CLOUD("cloud");

    companion object {
        fun fromStorageValue(value: String): SpeakerLlmExecutionMode {
            return entries.firstOrNull { it.storageValue == value } ?: CLOUD
        }
    }
}
