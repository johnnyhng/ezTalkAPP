package com.k2fsa.sherpa.onnx.simulate.streaming.asr.data.classes

data class Transcript(
    val recognizedText: String,
    var modifiedText: String = recognizedText,
    var wavFilePath: String,
    var checked: Boolean = false,
    var canCheck: Boolean = true,
    var remoteCandidates: List<String> = emptyList()
)
