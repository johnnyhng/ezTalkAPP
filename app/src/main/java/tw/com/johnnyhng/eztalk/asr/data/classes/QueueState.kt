package tw.com.johnnyhng.eztalk.asr.data.classes

data class QueueState(
    val currentText: String,
    val queue: List<String>
)
