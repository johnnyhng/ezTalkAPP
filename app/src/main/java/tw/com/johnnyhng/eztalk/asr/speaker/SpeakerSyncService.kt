package tw.com.johnnyhng.eztalk.asr.speaker

internal class SpeakerSyncService(
    private val localRepository: SpeakerLocalRepository,
    private val cloudRepository: SpeakerCloudRepository
) {
    suspend fun listRemoteFolders(cloudUserId: String): List<SpeakerRemoteFolder> {
        return cloudRepository.listRemoteFolders(cloudUserId)
    }

    suspend fun uploadAllToCloud(
        localUserId: String,
        cloudUserId: String,
        onProgress: (SpeakerSyncProgress) -> Unit = {}
    ): SpeakerUploadSummary {
        val localFolders = localRepository.listLocalFolders(localUserId)
        val allDocuments = localFolders.flatMap { it.documents }
        var uploadedDocuments = 0
        var skippedDocuments = 0

        allDocuments.forEachIndexed { index, document ->
            cloudRepository.uploadDocument(
                userId = cloudUserId,
                folderName = document.folderName,
                fileName = document.fileName,
                fullText = document.fullText,
                contentHash = document.contentHash
            )
            uploadedDocuments += 1
            onProgress(
                SpeakerSyncProgress(
                    current = index + 1,
                    total = allDocuments.size,
                    targetName = document.folderName
                )
            )
        }

        return SpeakerUploadSummary(
            uploadedFolders = localFolders.size,
            uploadedDocuments = uploadedDocuments,
            skippedDocuments = skippedDocuments
        )
    }

    suspend fun importRemoteFolders(
        localUserId: String,
        cloudUserId: String,
        remoteFolders: List<SpeakerRemoteFolder>,
        conflictPolicy: SpeakerSyncConflictPolicy = SpeakerSyncConflictPolicy.OVERWRITE,
        onProgress: (SpeakerSyncProgress) -> Unit = {}
    ): SpeakerImportSummary {
        val localDocuments = localRepository.listLocalFolders(localUserId)
            .flatMap { folder -> folder.documents.map { document -> "${folder.folderName}/${document.fileName}" to document } }
            .toMap()
        val remoteDocuments = remoteFolders.flatMap { folder ->
            cloudRepository.listRemoteDocuments(cloudUserId, folder.id)
        }

        var importedDocuments = 0
        var skippedDocuments = 0
        remoteDocuments.forEachIndexed { index, remoteDocument ->
            val localKey = "${remoteDocument.folderName}/${remoteDocument.fileName}"
            val existingDocument = localDocuments[localKey]
            val shouldSkip = conflictPolicy == SpeakerSyncConflictPolicy.SKIP_EXISTING &&
                existingDocument != null
            if (shouldSkip) {
                skippedDocuments += 1
            } else {
                val fullText = cloudRepository.downloadDocument(cloudUserId, remoteDocument)
                localRepository.createOrUpdateDocument(
                    userId = localUserId,
                    folderName = remoteDocument.folderName,
                    fileName = remoteDocument.fileName,
                    fullText = fullText
                )
                importedDocuments += 1
            }
            onProgress(
                SpeakerSyncProgress(
                    current = index + 1,
                    total = remoteDocuments.size,
                    targetName = remoteDocument.folderName
                )
            )
        }

        return SpeakerImportSummary(
            importedFolders = remoteFolders.size,
            importedDocuments = importedDocuments,
            skippedDocuments = skippedDocuments
        )
    }
}
