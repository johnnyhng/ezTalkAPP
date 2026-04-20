package tw.com.johnnyhng.eztalk.asr.data.classes

data class Transcript(
    val recognizedText: String,
    var modifiedText: String = recognizedText,
    var wavFilePath: String,
    var checked: Boolean = false,
    var mutable: Boolean = true,
    var removable: Boolean = false,
    var utteranceVariants: List<String> = emptyList(),
    var localCandidates: List<String> = emptyList(),
    var remoteCandidates: List<String> = emptyList()
)
