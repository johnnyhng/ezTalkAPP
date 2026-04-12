package tw.com.johnnyhng.eztalk.asr.speaker

import androidx.compose.runtime.Immutable

@Immutable
internal data class SpeakerLocalFolder(
    val folderName: String,
    val documents: List<SpeakerLocalDocument>
)

@Immutable
internal data class SpeakerLocalDocument(
    val folderName: String,
    val fileName: String,
    val filePath: String,
    val fullText: String,
    val contentHash: String
)

@Immutable
internal data class SpeakerRemoteFolder(
    val id: String,
    val folderName: String,
    val documentCount: Int,
    val updatedAtEpochMillis: Long
)

@Immutable
internal data class SpeakerRemoteDocument(
    val id: String,
    val folderId: String,
    val folderName: String,
    val fileName: String,
    val storagePath: String,
    val contentHash: String,
    val sizeBytes: Long,
    val updatedAtEpochMillis: Long
)

internal data class SpeakerSyncProgress(
    val current: Int,
    val total: Int,
    val targetName: String = ""
)

internal enum class SpeakerSyncDirection {
    UPLOAD,
    IMPORT
}

internal enum class SpeakerSyncConflictPolicy {
    OVERWRITE,
    SKIP_EXISTING
}

internal data class SpeakerUploadSummary(
    val uploadedFolders: Int,
    val uploadedDocuments: Int,
    val skippedDocuments: Int
)

internal data class SpeakerImportSummary(
    val importedFolders: Int,
    val importedDocuments: Int,
    val skippedDocuments: Int
)
