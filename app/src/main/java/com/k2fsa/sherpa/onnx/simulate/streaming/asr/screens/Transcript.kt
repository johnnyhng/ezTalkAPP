package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

data class Transcript(
    val recognizedText: String,
    var modifiedText: String = recognizedText,
    var wavFilePath: String,
    var checked: Boolean = false
)
