package tw.com.johnnyhng.eztalk.asr.data.classes

data class Model(
    val name: String,
    val modelPath: String,
    val tokensPath: String,
    val url: String? = null // For future download/version check
)