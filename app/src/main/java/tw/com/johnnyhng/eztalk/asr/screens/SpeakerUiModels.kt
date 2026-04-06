package tw.com.johnnyhng.eztalk.asr.screens

import androidx.compose.runtime.Immutable

@Immutable
internal data class SpeakerDirectoryUi(
    val id: String,
    val displayName: String,
    val isExpanded: Boolean,
    val documents: List<SpeakerDocumentUi>
)

@Immutable
internal data class SpeakerDocumentUi(
    val id: String,
    val displayName: String,
    val previewText: String,
    val fullText: String
)
