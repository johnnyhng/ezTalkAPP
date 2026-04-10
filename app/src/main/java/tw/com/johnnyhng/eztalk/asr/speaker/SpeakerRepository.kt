package tw.com.johnnyhng.eztalk.asr.speaker

import android.content.Context
import java.io.File

internal class SpeakerRepository(private val context: Context) {
    fun loadDirectories(
        userId: String,
        existingDirectories: List<SpeakerDirectoryUi>
    ): List<SpeakerDirectoryUi> {
        val expansionMap = existingDirectories.associate { it.displayName to it.isExpanded }
        val speechRoot = getSpeakerRootDirectory(context.filesDir, userId)
        if (!speechRoot.exists()) {
            speechRoot.mkdirs()
            return emptyList()
        }

        return speechRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?.map { directory ->
                val documents = directory.listFiles()
                    ?.filter { it.isFile && it.name.lowercase().endsWith(".txt") }
                    ?.sortedBy { it.name.lowercase() }
                    ?.mapNotNull { file ->
                        val fullText = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
                        SpeakerDocumentUi(
                            id = file.absolutePath,
                            displayName = file.name,
                            previewText = buildPreviewText(fullText),
                            fullText = fullText
                        )
                    }
                    .orEmpty()

                SpeakerDirectoryUi(
                    id = directory.absolutePath,
                    displayName = directory.name,
                    isExpanded = expansionMap[directory.name] ?: false,
                    documents = documents
                )
            }
            .orEmpty()
    }

    fun createFolder(userId: String, folderName: String): FolderCreationResult {
        val speechRoot = getSpeakerRootDirectory(context.filesDir, userId)
        if (!speechRoot.exists() && !speechRoot.mkdirs()) {
            return FolderCreationResult.FAILED
        }

        val folder = File(speechRoot, folderName)
        if (folder.exists()) {
            return FolderCreationResult.ALREADY_EXISTS
        }

        return if (folder.mkdirs()) {
            FolderCreationResult.CREATED
        } else {
            FolderCreationResult.FAILED
        }
    }

    fun deleteFolder(userId: String, folderName: String): Boolean {
        val target = File(getSpeakerRootDirectory(context.filesDir, userId), folderName)
        return target.deleteRecursively()
    }

    fun deleteDocument(filePath: String): Boolean {
        val target = File(filePath)
        return target.exists() && target.delete()
    }

    fun saveDocument(filePath: String, updatedText: String): Boolean {
        return runCatching {
            File(filePath).writeText(updatedText)
        }.isSuccess
    }
}

internal enum class FolderCreationResult {
    CREATED,
    ALREADY_EXISTS,
    FAILED
}

internal fun getSpeakerRootDirectory(filesDir: File, userId: String): File {
    return File(filesDir, "speech/$userId")
}

internal fun sanitizeFolderName(input: String): String {
    return input.trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
}

private fun buildPreviewText(text: String): String {
    val normalized = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    if (normalized.isBlank()) {
        return ""
    }
    return if (normalized.length > 80) {
        normalized.take(80) + "..."
    } else {
        normalized
    }
}
