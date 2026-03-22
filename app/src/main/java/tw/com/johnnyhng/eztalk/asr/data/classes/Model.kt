package tw.com.johnnyhng.eztalk.asr.data.classes

data class Model(
    val name: String,
    val modelPath: String,
    val tokensPath: String,
    val url: String? = null
) {
    companion object {
        val MODELS = listOf(
            Model(
                name = "Multi-language",
                modelPath = "sherpa-onnx-whisper-tiny.en/whisper-tiny.en.onnx",
                tokensPath = "sherpa-onnx-whisper-tiny.en/tokens.txt"
            )
            // 你可以在此加入更多預設模型
        )
    }
}
