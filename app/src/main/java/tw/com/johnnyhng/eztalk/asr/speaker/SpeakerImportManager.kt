package tw.com.johnnyhng.eztalk.asr.speaker

import android.content.Context
import android.net.Uri
import java.io.File

sealed interface MultiTextImportResult {
    data class Success(val folderName: String, val importedCount: Int) : MultiTextImportResult
    data object NoFiles : MultiTextImportResult
    data object Failed : MultiTextImportResult
}

fun importTextUrisIntoSpeakerFolder(
    context: Context,
    sourceUris: List<Uri>,
    filesDir: File,
    userId: String,
    folderName: String,
    onProgress: ((current: Int, total: Int) -> Unit)? = null
): MultiTextImportResult {
    return try {
        val targetRoot = getSpeakerRootDirectory(filesDir, userId)
        if (!targetRoot.exists() && !targetRoot.mkdirs()) {
            return MultiTextImportResult.Failed
        }
        val targetDirectory = File(targetRoot, folderName)
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            return MultiTextImportResult.Failed
        }

        var importedCount = 0
        val totalCount = sourceUris.size
        sourceUris.forEachIndexed { index, uri ->
            val sourceName = queryDisplayName(context, uri)
                ?.takeIf { it.isNotBlank() }
                ?: "imported.txt"
            val targetFile = uniqueTargetFile(targetDirectory, ensureTxtExtension(sourceName))
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                importedCount++
            }
            onProgress?.invoke(index + 1, totalCount)
        }

        when {
            sourceUris.isEmpty() -> MultiTextImportResult.NoFiles
            importedCount == 0 -> MultiTextImportResult.Failed
            else -> MultiTextImportResult.Success(
                folderName = targetDirectory.name,
                importedCount = importedCount
            )
        }
    } catch (_: Exception) {
        MultiTextImportResult.Failed
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index != -1 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}

private fun ensureTxtExtension(name: String): String {
    return if (name.lowercase().endsWith(".txt")) name else "$name.txt"
}

private fun uniqueTargetFile(directory: File, requestedName: String): File {
    val baseName = requestedName.substringBeforeLast('.', requestedName)
    val extension = requestedName.substringAfterLast('.', "txt")
    var candidate = File(directory, requestedName)
    var counter = 1
    while (candidate.exists()) {
        candidate = File(directory, "${baseName}_$counter.$extension")
        counter++
    }
    return candidate
}
