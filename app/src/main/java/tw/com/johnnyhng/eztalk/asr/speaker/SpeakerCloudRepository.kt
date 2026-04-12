package tw.com.johnnyhng.eztalk.asr.speaker

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

internal interface SpeakerCloudRepository {
    suspend fun listRemoteFolders(userId: String): List<SpeakerRemoteFolder>
    suspend fun listRemoteDocuments(userId: String, folderId: String): List<SpeakerRemoteDocument>
    suspend fun uploadDocument(
        userId: String,
        folderName: String,
        fileName: String,
        fullText: String,
        contentHash: String
    ): SpeakerRemoteDocument

    suspend fun downloadDocument(userId: String, document: SpeakerRemoteDocument): String
}

internal class FirebaseSpeakerCloudRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : SpeakerCloudRepository {
    override suspend fun listRemoteFolders(userId: String): List<SpeakerRemoteFolder> {
        return folderCollection(userId)
            .get()
            .await()
            .documents
            .map { document ->
                SpeakerRemoteFolder(
                    id = document.id,
                    folderName = document.getString("folderName").orEmpty(),
                    documentCount = (document.getLong("documentCount") ?: 0L).toInt(),
                    updatedAtEpochMillis = document.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                )
            }
            .sortedBy { it.folderName.lowercase() }
    }

    override suspend fun listRemoteDocuments(userId: String, folderId: String): List<SpeakerRemoteDocument> {
        val folderSnapshot = folderCollection(userId).document(folderId).get().await()
        val folderName = folderSnapshot.getString("folderName").orEmpty()
        return folderCollection(userId)
            .document(folderId)
            .collection("documents")
            .get()
            .await()
            .documents
            .map { document ->
                SpeakerRemoteDocument(
                    id = document.id,
                    folderId = folderId,
                    folderName = folderName,
                    fileName = document.getString("fileName").orEmpty(),
                    storagePath = document.getString("storagePath").orEmpty(),
                    contentHash = document.getString("contentHash").orEmpty(),
                    sizeBytes = document.getLong("sizeBytes") ?: 0L,
                    updatedAtEpochMillis = document.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                )
            }
            .sortedBy { it.fileName.lowercase() }
    }

    override suspend fun uploadDocument(
        userId: String,
        folderName: String,
        fileName: String,
        fullText: String,
        contentHash: String
    ): SpeakerRemoteDocument {
        val folderId = sanitizeFolderName(folderName)
        val documentId = sanitizeRemoteDocumentId(fileName)
        val storagePath = "speaker/$userId/$folderId/$documentId"
        val bytes = fullText.toByteArray()
        storage.reference.child(storagePath).putBytes(bytes).await()

        val now = Timestamp.now()
        folderCollection(userId)
            .document(folderId)
            .set(
                mapOf(
                    "folderName" to folderName,
                    "normalizedFolderName" to folderId,
                    "updatedAt" to now,
                    "documentCount" to FieldValue.increment(1)
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()

        folderCollection(userId)
            .document(folderId)
            .collection("documents")
            .document(documentId)
            .set(
                mapOf(
                    "fileName" to fileName,
                    "normalizedFileName" to documentId,
                    "storagePath" to storagePath,
                    "contentHash" to contentHash,
                    "sizeBytes" to bytes.size.toLong(),
                    "updatedAt" to now
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()

        val documentCount = folderCollection(userId)
            .document(folderId)
            .collection("documents")
            .get()
            .await()
            .size()

        folderCollection(userId)
            .document(folderId)
            .update(
                mapOf(
                    "documentCount" to documentCount,
                    "updatedAt" to now
                )
            )
            .await()

        return SpeakerRemoteDocument(
            id = documentId,
            folderId = folderId,
            folderName = folderName,
            fileName = fileName,
            storagePath = storagePath,
            contentHash = contentHash,
            sizeBytes = bytes.size.toLong(),
            updatedAtEpochMillis = now.toDate().time
        )
    }

    override suspend fun downloadDocument(userId: String, document: SpeakerRemoteDocument): String {
        val storagePath = document.storagePath.ifBlank {
            "speaker/$userId/${document.folderId}/${document.id}"
        }
        val bytes = storage.reference.child(storagePath).getBytes(MAX_DOWNLOAD_BYTES).await()
        return bytes.toString(Charsets.UTF_8)
    }

    private fun folderCollection(userId: String) = firestore
        .collection("users")
        .document(userId)
        .collection("speakerFolders")

    private fun sanitizeRemoteDocumentId(fileName: String): String {
        return fileName.trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 1024 * 1024L
    }
}
