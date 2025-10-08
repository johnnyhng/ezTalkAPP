package com.k2fsa.sherpa.onnx.simulate.streaming.asr

data class Model(
    val name: String,
    val modelPath: String,
    val tokensPath: String,
    val url: String? = null // For future download/version check
)
