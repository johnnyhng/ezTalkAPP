package tw.com.johnnyhng.eztalk.asr.screens

import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import java.io.File
import java.util.Locale

@Composable
fun SpeakerScreen(homeViewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userSettings by homeViewModel.userSettings.collectAsState()
    val userId = userSettings.userId

    var directories by remember(userId) { mutableStateOf<List<SpeakerDirectoryUi>>(emptyList()) }
    var selectedDocumentId by remember(userId) { mutableStateOf<String?>(null) }
    var isLoading by remember(userId) { mutableStateOf(true) }
    var newFolderName by remember(userId) { mutableStateOf("") }
    var showCreateFolderDialog by remember(userId) { mutableStateOf(false) }
    var createFolderDialogError by remember(userId) { mutableStateOf<String?>(null) }
    var importTargetDirectory by remember(userId) { mutableStateOf<String?>(null) }
    var driveImportFolderName by remember(userId) { mutableStateOf("") }
    var showDriveImportDialog by remember(userId) { mutableStateOf(false) }
    var driveImportDialogError by remember(userId) { mutableStateOf<String?>(null) }
    var isImporting by remember(userId) { mutableStateOf(false) }
    var importProgressCurrent by remember(userId) { mutableStateOf(0) }
    var importProgressTotal by remember(userId) { mutableStateOf(0) }
    var importProgressFolderName by remember(userId) { mutableStateOf("") }
    var isEditingDocument by remember(userId) { mutableStateOf(false) }
    var editingText by remember(userId) { mutableStateOf("") }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var currentPlayingDocumentId by remember { mutableStateOf<String?>(null) }
    var playbackDocumentId by remember { mutableStateOf<String?>(null) }
    var playbackSegments by remember { mutableStateOf<List<String>>(emptyList()) }
    var playbackSegmentIndex by remember { mutableStateOf(0) }
    var isPlaybackPaused by remember { mutableStateOf(false) }

    val selectedDocument = directories
        .flatMap { it.documents }
        .firstOrNull { it.id == selectedDocumentId }
    val isSelectedDocumentPlaying = selectedDocument?.id != null && selectedDocument.id == currentPlayingDocumentId
    val isSelectedDocumentPaused = selectedDocument?.id != null &&
        selectedDocument.id == playbackDocumentId &&
        isPlaybackPaused

    LaunchedEffect(selectedDocument?.id) {
        isEditingDocument = false
        editingText = selectedDocument?.fullText.orEmpty()
    }

    LaunchedEffect(selectedDocument?.fullText) {
        if (!isEditingDocument) {
            editingText = selectedDocument?.fullText.orEmpty()
        }
    }

    fun resetPlaybackState() {
        currentPlayingDocumentId = null
        playbackDocumentId = null
        playbackSegments = emptyList()
        playbackSegmentIndex = 0
        isPlaybackPaused = false
    }

    fun speakSegment(documentId: String, segmentIndex: Int) {
        val segment = playbackSegments.getOrNull(segmentIndex)
        if (segment == null) {
            resetPlaybackState()
            return
        }
        playbackDocumentId = documentId
        playbackSegmentIndex = segmentIndex
        isPlaybackPaused = false
        currentPlayingDocumentId = documentId
        tts?.speak(
            segment,
            TextToSpeech.QUEUE_FLUSH,
            null,
            buildSpeakerUtteranceId(documentId, segmentIndex)
        )
    }

    fun setSelectedDocumentIfNeeded(updatedDirectories: List<SpeakerDirectoryUi>) {
        val allDocuments = updatedDirectories.flatMap { it.documents }
        selectedDocumentId = when {
            allDocuments.isEmpty() -> null
            allDocuments.any { it.id == selectedDocumentId } -> selectedDocumentId
            else -> null
        }
    }

    fun reloadDirectories() {
        scope.launch {
            isLoading = true
            val loadedDirectories = withContext(Dispatchers.IO) {
                loadSpeakerDirectories(context.filesDir, userId, directories)
            }
            directories = loadedDirectories
            setSelectedDocumentIfNeeded(loadedDirectories)
            isLoading = false
        }
    }

    LaunchedEffect(userId) {
        reloadDirectories()
    }

    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = if (
                    tts?.isLanguageAvailable(Locale.TRADITIONAL_CHINESE) == TextToSpeech.LANG_AVAILABLE
                ) {
                    Locale.TRADITIONAL_CHINESE
                } else {
                    Locale.getDefault()
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val parsed = utteranceId?.let(::parseSpeakerUtteranceId) ?: return
                        scope.launch {
                            playbackDocumentId = parsed.documentId
                            playbackSegmentIndex = parsed.segmentIndex
                            currentPlayingDocumentId = parsed.documentId
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        val parsed = utteranceId?.let(::parseSpeakerUtteranceId) ?: return
                        scope.launch {
                            if (playbackDocumentId != parsed.documentId || isPlaybackPaused) {
                                return@launch
                            }

                            val nextIndex = parsed.segmentIndex + 1
                            if (nextIndex < playbackSegments.size) {
                                speakSegment(parsed.documentId, nextIndex)
                            } else {
                                resetPlaybackState()
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        val parsed = utteranceId?.let(::parseSpeakerUtteranceId) ?: return
                        scope.launch {
                            if (playbackDocumentId == parsed.documentId) {
                                resetPlaybackState()
                            }
                        }
                    }
                })
                isTtsReady = true
            } else {
                isTtsReady = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
            resetPlaybackState()
        }
    }

    val txtImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val targetDirectory = importTargetDirectory
        importTargetDirectory = null
        if (uris.isEmpty() || targetDirectory == null) return@rememberLauncherForActivityResult

        scope.launch {
            isImporting = true
            importProgressCurrent = 0
            importProgressTotal = uris.size
            importProgressFolderName = targetDirectory
            val result = withContext(Dispatchers.IO) {
                importTextUrisIntoSpeakerFolder(
                    context = context,
                    sourceUris = uris,
                    filesDir = context.filesDir,
                    userId = userId,
                    folderName = targetDirectory,
                    onProgress = { current, total ->
                        scope.launch {
                            importProgressCurrent = current
                            importProgressTotal = total
                        }
                    }
                )
            }
            isImporting = false
            when (result) {
                is MultiTextImportResult.Success -> {
                    reloadDirectories()
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.speaker_import_many_success,
                            result.folderName,
                            result.importedCount
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                MultiTextImportResult.NoFiles -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.speaker_import_no_txt),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                MultiTextImportResult.Failed -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.speaker_import_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val driveFilesImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val targetFolderName = sanitizeFolderName(driveImportFolderName)
        if (targetFolderName.isBlank()) return@rememberLauncherForActivityResult

        scope.launch {
            isImporting = true
            importProgressCurrent = 0
            importProgressTotal = uris.size
            importProgressFolderName = targetFolderName
            val result = withContext(Dispatchers.IO) {
                importTextUrisIntoSpeakerFolder(
                    context = context,
                    sourceUris = uris,
                    filesDir = context.filesDir,
                    userId = userId,
                    folderName = targetFolderName,
                    onProgress = { current, total ->
                        scope.launch {
                            importProgressCurrent = current
                            importProgressTotal = total
                        }
                    }
                )
            }
            isImporting = false
            when (result) {
                is MultiTextImportResult.Success -> {
                    reloadDirectories()
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.speaker_import_many_success,
                            result.folderName,
                            result.importedCount
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                MultiTextImportResult.NoFiles -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.speaker_import_no_txt),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                MultiTextImportResult.Failed -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.speaker_import_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (showCreateFolderDialog) {
            AlertDialog(
                onDismissRequest = { showCreateFolderDialog = false },
                title = {
                    Text(text = stringResource(R.string.speaker_create_folder))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = {
                                newFolderName = it
                                createFolderDialogError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.speaker_folder_name_label)) },
                            placeholder = { Text(stringResource(R.string.speaker_folder_name_placeholder)) },
                            isError = createFolderDialogError != null
                        )
                        if (createFolderDialogError != null) {
                            Text(
                                text = createFolderDialogError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val sanitizedName = sanitizeFolderName(newFolderName)
                            if (sanitizedName.isBlank()) {
                                createFolderDialogError =
                                    context.getString(R.string.speaker_invalid_folder_name)
                                return@TextButton
                            }

                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    createSpeakerFolder(context.filesDir, userId, sanitizedName)
                                }
                                when (result) {
                                    FolderCreationResult.CREATED -> {
                                        newFolderName = ""
                                        showCreateFolderDialog = false
                                        reloadDirectories()
                                    }
                                    FolderCreationResult.ALREADY_EXISTS -> {
                                        createFolderDialogError =
                                            context.getString(R.string.speaker_folder_exists, sanitizedName)
                                    }
                                    FolderCreationResult.FAILED -> {
                                        createFolderDialogError =
                                            context.getString(R.string.speaker_create_folder_failed)
                                    }
                                }
                            }
                        },
                        enabled = newFolderName.isNotBlank()
                    ) {
                        Text(text = stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCreateFolderDialog = false
                            newFolderName = ""
                            createFolderDialogError = null
                        }
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (isImporting) {
            AlertDialog(
                onDismissRequest = { },
                title = {
                    Text(text = stringResource(R.string.speaker_importing_title))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(
                                R.string.speaker_importing_target,
                                importProgressFolderName
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = if (importProgressTotal == 0) 0f
                            else importProgressCurrent.toFloat() / importProgressTotal.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(
                                R.string.speaker_importing_progress,
                                importProgressCurrent,
                                importProgressTotal
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {}
            )
        }

        if (showDriveImportDialog) {
            AlertDialog(
                onDismissRequest = { showDriveImportDialog = false },
                title = {
                    Text(text = stringResource(R.string.speaker_google_drive))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = driveImportFolderName,
                            onValueChange = {
                                driveImportFolderName = it
                                driveImportDialogError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.speaker_import_target_folder_label)) },
                            placeholder = { Text(stringResource(R.string.speaker_import_target_folder_placeholder)) },
                            isError = driveImportDialogError != null
                        )
                        if (driveImportDialogError != null) {
                            Text(
                                text = driveImportDialogError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val sanitizedName = sanitizeFolderName(driveImportFolderName)
                            if (sanitizedName.isBlank()) {
                                driveImportDialogError =
                                    context.getString(R.string.speaker_invalid_folder_name)
                                return@TextButton
                            }
                            driveImportFolderName = sanitizedName
                            driveImportDialogError = null
                            showDriveImportDialog = false
                            driveFilesImportLauncher.launch(arrayOf("text/plain"))
                        },
                        enabled = driveImportFolderName.isNotBlank()
                    ) {
                        Text(text = stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDriveImportDialog = false
                            driveImportFolderName = ""
                            driveImportDialogError = null
                        }
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }

        SpeechFileExplorer(
            directories = directories,
            selectedDocumentId = selectedDocumentId,
            isLoading = isLoading,
            isImportEnabled = !isImporting,
            onCreateFolder = { showCreateFolderDialog = true },
            onGoogleDriveImport = { showDriveImportDialog = true },
            onToggleExpand = { directory ->
                val nextExpanded = !directory.isExpanded
                directories = directories.map {
                    when {
                        it.id == directory.id -> it.copy(isExpanded = nextExpanded)
                        nextExpanded -> it.copy(isExpanded = false)
                        else -> it
                    }
                }
            },
            onRefresh = { reloadDirectories() },
            onImportIntoDirectory = { directory ->
                importTargetDirectory = directory.displayName
                txtImportLauncher.launch(arrayOf("text/plain"))
            },
            onRemoveDirectory = { directory ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        deleteSpeakerFolder(context.filesDir, userId, directory.displayName)
                    }
                    val updatedDirectories = directories.filterNot { it.id == directory.id }
                    directories = updatedDirectories
                    setSelectedDocumentIfNeeded(updatedDirectories)
                }
            },
            onDocumentSelected = { documentId ->
                selectedDocumentId = documentId
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(0.42f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        SpeakerContentScreen(
            selectedDocument = selectedDocument,
            isTtsReady = isTtsReady,
            isPlaying = isSelectedDocumentPlaying,
            isPaused = isSelectedDocumentPaused,
            isEditing = isEditingDocument,
            editingText = editingText,
            onEditingTextChange = { editingText = it },
            onPlay = {
                if (selectedDocument == null) return@SpeakerContentScreen
                if (!isTtsReady) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.speaker_tts_not_ready),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@SpeakerContentScreen
                }
                if (selectedDocument.fullText.isBlank()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.speaker_empty_text_file),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@SpeakerContentScreen
                }
                tts?.stop()
                if (isSelectedDocumentPaused && playbackSegments.isNotEmpty()) {
                    speakSegment(selectedDocument.id, playbackSegmentIndex)
                } else {
                    val segments = segmentTextForTts(selectedDocument.fullText)
                    if (segments.isEmpty()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.speaker_empty_text_file),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@SpeakerContentScreen
                    }
                    playbackSegments = segments
                    speakSegment(selectedDocument.id, 0)
                }
            },
            onPause = {
                if (!isSelectedDocumentPlaying) return@SpeakerContentScreen
                tts?.stop()
                currentPlayingDocumentId = null
                isPlaybackPaused = true
            },
            onStop = {
                tts?.stop()
                resetPlaybackState()
            },
            onEdit = {
                if (selectedDocument == null) return@SpeakerContentScreen
                tts?.stop()
                resetPlaybackState()
                editingText = selectedDocument.fullText
                isEditingDocument = true
            },
            onSave = {
                if (selectedDocument == null) return@SpeakerContentScreen
                val updatedText = editingText
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching {
                            File(selectedDocument.id).writeText(updatedText)
                        }.isSuccess
                    }
                    if (saved) {
                        isEditingDocument = false
                        reloadDirectories()
                        Toast.makeText(
                            context,
                            context.getString(R.string.speaker_save_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.speaker_save_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onCancelEdit = {
                editingText = selectedDocument?.fullText.orEmpty()
                isEditingDocument = false
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(0.58f)
        )
    }
}

private enum class FolderCreationResult {
    CREATED,
    ALREADY_EXISTS,
    FAILED
}

private fun createSpeakerFolder(filesDir: File, userId: String, folderName: String): FolderCreationResult {
    val speechRoot = getSpeakerRootDirectory(filesDir, userId)
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

private fun deleteSpeakerFolder(filesDir: File, userId: String, folderName: String): Boolean {
    val target = File(getSpeakerRootDirectory(filesDir, userId), folderName)
    return target.deleteRecursively()
}

private fun loadSpeakerDirectories(
    filesDir: File,
    userId: String,
    existingDirectories: List<SpeakerDirectoryUi>
): List<SpeakerDirectoryUi> {
    val expansionMap = existingDirectories.associate { it.displayName to it.isExpanded }
    val speechRoot = getSpeakerRootDirectory(filesDir, userId)
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

internal fun getSpeakerRootDirectory(filesDir: File, userId: String): File {
    return File(filesDir, "speech/$userId")
}

private fun sanitizeFolderName(input: String): String {
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

private data class SpeakerUtteranceId(
    val documentId: String,
    val segmentIndex: Int
)

private fun buildSpeakerUtteranceId(documentId: String, segmentIndex: Int): String {
    return "$segmentIndex::$documentId"
}

private fun parseSpeakerUtteranceId(utteranceId: String): SpeakerUtteranceId? {
    val separatorIndex = utteranceId.indexOf("::")
    if (separatorIndex <= 0) return null
    val segmentIndex = utteranceId.substring(0, separatorIndex).toIntOrNull() ?: return null
    val documentId = utteranceId.substring(separatorIndex + 2)
    if (documentId.isBlank()) return null
    return SpeakerUtteranceId(
        documentId = documentId,
        segmentIndex = segmentIndex
    )
}

private fun segmentTextForTts(text: String): List<String> {
    val normalized = text
        .replace("\r\n", "\n")
        .trim()
    if (normalized.isBlank()) return emptyList()

    return normalized
        .split('\n')
        .flatMap { paragraph ->
            paragraph
                .split(Regex("(?<=[。！？!?；;])"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
        .flatMap { chunkTextForTts(it) }
}

private fun chunkTextForTts(text: String, maxLength: Int = 180): List<String> {
    if (text.length <= maxLength) return listOf(text)

    val chunks = mutableListOf<String>()
    var remaining = text.trim()
    while (remaining.length > maxLength) {
        val splitIndex = remaining.lastIndexOf(' ', startIndex = maxLength)
            .takeIf { it > maxLength / 2 }
            ?: maxLength
        chunks += remaining.substring(0, splitIndex).trim()
        remaining = remaining.substring(splitIndex).trim()
    }
    if (remaining.isNotBlank()) {
        chunks += remaining
    }
    return chunks
}
