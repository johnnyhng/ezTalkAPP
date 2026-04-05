package tw.com.johnnyhng.eztalk.asr.screens

import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
    var importTargetDirectory by remember(userId) { mutableStateOf<String?>(null) }
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
            else -> allDocuments.first().id
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

    val txtImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val targetDirectory = importTargetDirectory
        importTargetDirectory = null
        if (uri == null || targetDirectory == null) return@rememberLauncherForActivityResult

        scope.launch {
            val importedFileName = withContext(Dispatchers.IO) {
                importTextIntoSpeakerFolder(
                    context = context,
                    sourceUri = uri,
                    filesDir = context.filesDir,
                    userId = userId,
                    folderName = targetDirectory
                )
            }
            if (importedFileName == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.speaker_import_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                reloadDirectories()
                Toast.makeText(
                    context,
                    context.getString(R.string.speaker_import_success, importedFileName),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.speaker_placeholder_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.speaker_placeholder_description, userId),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SpeakerOverviewHeader(
                    folderName = newFolderName,
                    onFolderNameChange = { newFolderName = it },
                    onCreateFolder = {
                        val sanitizedName = sanitizeFolderName(newFolderName)
                        if (sanitizedName.isBlank()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.speaker_invalid_folder_name),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@SpeakerOverviewHeader
                        }

                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                createSpeakerFolder(context.filesDir, userId, sanitizedName)
                            }
                            when (result) {
                                FolderCreationResult.CREATED -> {
                                    newFolderName = ""
                                    reloadDirectories()
                                }
                                FolderCreationResult.ALREADY_EXISTS -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.speaker_folder_exists, sanitizedName),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                FolderCreationResult.FAILED -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.speaker_create_folder_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    onGoogleDriveImport = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.speaker_select_folder_for_import),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
                SpeakerDivider()
                when {
                    isLoading -> {
                        SpeakerCenteredState(
                            text = stringResource(R.string.speaker_loading),
                            showProgress = true
                        )
                    }

                    directories.isEmpty() -> {
                        SpeakerCenteredState(
                            text = stringResource(R.string.speaker_empty_folders)
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            items(directories, key = { it.id }) { directory ->
                                SpeakerDirectorySection(
                                    directory = directory,
                                    selectedDocumentId = selectedDocumentId,
                                    onToggleExpand = {
                                        directories = directories.map {
                                            if (it.id == directory.id) {
                                                it.copy(isExpanded = !it.isExpanded)
                                            } else {
                                                it
                                            }
                                        }
                                    },
                                    onRefresh = { reloadDirectories() },
                                    onImport = {
                                        importTargetDirectory = directory.displayName
                                        txtImportLauncher.launch("text/*")
                                    },
                                    onRemove = {
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
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SpeakerPlaybackHeader(
                    selectedDocument = selectedDocument,
                    isTtsReady = isTtsReady,
                    isPlaying = isSelectedDocumentPlaying,
                    isPaused = isSelectedDocumentPaused,
                    onPlay = {
                        if (selectedDocument == null) return@SpeakerPlaybackHeader
                        if (!isTtsReady) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.speaker_tts_not_ready),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@SpeakerPlaybackHeader
                        }
                        if (selectedDocument.fullText.isBlank()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.speaker_empty_text_file),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@SpeakerPlaybackHeader
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
                                return@SpeakerPlaybackHeader
                            }
                            playbackSegments = segments
                            speakSegment(selectedDocument.id, 0)
                        }
                    },
                    onPause = {
                        if (!isSelectedDocumentPlaying) return@SpeakerPlaybackHeader
                        tts?.stop()
                        currentPlayingDocumentId = null
                        isPlaybackPaused = true
                    },
                    onStop = {
                        tts?.stop()
                        resetPlaybackState()
                    }
                )
                SpeakerDivider()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (selectedDocument == null) {
                        Text(
                            text = stringResource(R.string.speaker_no_document_selected),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = selectedDocument.displayName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = selectedDocument.fullText,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakerCenteredState(
    text: String,
    showProgress: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showProgress) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SpeakerDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun SpeakerOverviewHeader(
    folderName: String,
    onFolderNameChange: (String) -> Unit,
    onCreateFolder: () -> Unit,
    onGoogleDriveImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.speaker_overview_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.speaker_overview_subtitle),
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = folderName,
            onValueChange = onFolderNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.speaker_folder_name_label)) },
            placeholder = { Text(stringResource(R.string.speaker_folder_name_placeholder)) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCreateFolder,
                enabled = folderName.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.speaker_create_folder))
            }
            Button(
                onClick = onGoogleDriveImport,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.speaker_google_drive))
            }
        }
    }
}

@Composable
private fun SpeakerDirectorySection(
    directory: SpeakerDirectoryUi,
    selectedDocumentId: String?,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    onDocumentSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (directory.isExpanded) "-" else "+",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = directory.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.speaker_refresh_folder)
                )
            }
            IconButton(
                onClick = onImport,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = stringResource(R.string.speaker_import_txt)
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.speaker_remove_folder)
                )
            }
        }

        if (directory.isExpanded) {
            if (directory.documents.isEmpty()) {
                Text(
                    text = stringResource(R.string.speaker_empty_documents),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, top = 6.dp)
                ) {
                    directory.documents.forEach { document ->
                        SpeakerDocumentRow(
                            document = document,
                            isSelected = document.id == selectedDocumentId,
                            onClick = { onDocumentSelected(document.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakerDocumentRow(
    document: SpeakerDocumentUi,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = document.displayName,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = document.previewText.ifBlank { stringResource(R.string.speaker_empty_text_file) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SpeakerPlaybackHeader(
    selectedDocument: SpeakerDocumentUi?,
    isTtsReady: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = selectedDocument?.displayName
                    ?: stringResource(R.string.speaker_no_document_selected),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.speaker_playback_subtitle),
                style = MaterialTheme.typography.bodySmall
            )
        }
        SpeakerPlaybackAction(
            icon = Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.play),
            enabled = selectedDocument != null && isTtsReady && !isPlaying,
            onClick = onPlay
        )
        SpeakerPlaybackAction(
            icon = Icons.Filled.Pause,
            contentDescription = stringResource(R.string.speaker_pause),
            enabled = isPlaying,
            onClick = onPause
        )
        SpeakerPlaybackAction(
            icon = Icons.Filled.Stop,
            contentDescription = stringResource(R.string.stop),
            enabled = isPlaying || isPaused,
            onClick = onStop
        )
    }
}

@Composable
private fun SpeakerPlaybackAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Immutable
private data class SpeakerDirectoryUi(
    val id: String,
    val displayName: String,
    val isExpanded: Boolean,
    val documents: List<SpeakerDocumentUi>
)

@Immutable
private data class SpeakerDocumentUi(
    val id: String,
    val displayName: String,
    val previewText: String,
    val fullText: String
)

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
                isExpanded = expansionMap[directory.name] ?: true,
                documents = documents
            )
        }
        .orEmpty()
}

private fun getSpeakerRootDirectory(filesDir: File, userId: String): File {
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

private fun importTextIntoSpeakerFolder(
    context: android.content.Context,
    sourceUri: Uri,
    filesDir: File,
    userId: String,
    folderName: String
): String? {
    val sourceName = queryDisplayName(context, sourceUri)
        ?.takeIf { it.isNotBlank() }
        ?: "imported.txt"
    val safeName = ensureTxtExtension(sourceName)
    val targetDirectory = File(getSpeakerRootDirectory(filesDir, userId), folderName)
    if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
        return null
    }

    val targetFile = uniqueTargetFile(targetDirectory, safeName)
    return try {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null
        targetFile.name
    } catch (_: Exception) {
        null
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
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
