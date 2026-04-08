package tw.com.johnnyhng.eztalk.asr.ui.speaker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tw.com.johnnyhng.eztalk.asr.R

@Composable
internal fun SpeechFileExplorer(
    directories: List<SpeakerDirectoryUi>,
    selectedDocumentId: String?,
    isLoading: Boolean,
    localAsrText: String = "",
    isLocalAsrRecording: Boolean = false,
    localAsrCountdownProgress: Float = 0f,
    isLocalAsrEnabled: Boolean = true,
    isImportEnabled: Boolean,
    isDirectoryDeleteEnabled: Boolean,
    isDocumentDeleteEnabled: Boolean,
    onLocalAsrClick: () -> Unit = {},
    onCreateFolder: () -> Unit,
    onGoogleDriveImport: () -> Unit,
    onToggleExpand: (SpeakerDirectoryUi) -> Unit,
    onRefresh: () -> Unit,
    onImportIntoDirectory: (SpeakerDirectoryUi) -> Unit,
    onRemoveDirectory: (SpeakerDirectoryUi) -> Unit,
    onRemoveDocument: (SpeakerDocumentUi) -> Unit,
    onDocumentSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shouldShowLocalAsrWidget = directories.any { it.isExpanded }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SpeakerOverviewHeader(
                onCreateFolder = onCreateFolder,
                onGoogleDriveImport = onGoogleDriveImport,
                isImportEnabled = isImportEnabled
            )
            SpeakerDivider()
            when {
                isLoading -> {
                    SpeakerCenteredState(
                        text = stringResource(R.string.speaker_loading),
                        showProgress = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }

                directories.isEmpty() -> {
                    SpeakerCenteredState(
                        text = stringResource(R.string.speaker_empty_folders),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        items(directories, key = { it.id }) { directory ->
                            SpeakerDirectorySection(
                                directory = directory,
                                selectedDocumentId = selectedDocumentId,
                                onToggleExpand = { onToggleExpand(directory) },
                                onRefresh = onRefresh,
                                onImport = { onImportIntoDirectory(directory) },
                                isImportEnabled = isImportEnabled,
                                onRemove = { onRemoveDirectory(directory) },
                                isDirectoryDeleteEnabled = isDirectoryDeleteEnabled,
                                onRemoveDocument = onRemoveDocument,
                                isDocumentDeleteEnabled = isDocumentDeleteEnabled,
                                onDocumentSelected = onDocumentSelected
                            )
                        }
                    }
                }
            }
            if (shouldShowLocalAsrWidget) {
                SpeakerDivider()
                LocalASRWidget(
                    recognizedText = localAsrText,
                    isRecording = isLocalAsrRecording,
                    countdownProgress = localAsrCountdownProgress,
                    isEnabled = isLocalAsrEnabled,
                    onMicClick = onLocalAsrClick,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SpeakerCenteredState(
    text: String,
    showProgress: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
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
internal fun SpeakerDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun SpeakerOverviewHeader(
    onCreateFolder: () -> Unit,
    onGoogleDriveImport: () -> Unit,
    isImportEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.speaker_overview_title),
            style = MaterialTheme.typography.titleMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onCreateFolder) {
                Icon(
                    imageVector = Icons.Filled.CreateNewFolder,
                    contentDescription = stringResource(R.string.speaker_create_folder)
                )
            }
            IconButton(
                onClick = onGoogleDriveImport,
                enabled = isImportEnabled
            ) {
                Icon(
                    imageVector = Icons.Filled.Cloud,
                    contentDescription = stringResource(R.string.speaker_google_drive)
                )
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
    isImportEnabled: Boolean,
    onRemove: () -> Unit,
    isDirectoryDeleteEnabled: Boolean,
    onRemoveDocument: (SpeakerDocumentUi) -> Unit,
    isDocumentDeleteEnabled: Boolean,
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
                enabled = isImportEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(R.string.speaker_import_txt)
                )
            }
            IconButton(
                onClick = onRemove,
                enabled = isDirectoryDeleteEnabled,
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
                            onClick = { onDocumentSelected(document.id) },
                            onRemove = { onRemoveDocument(document) },
                            isDeleteEnabled = isDocumentDeleteEnabled
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
    onClick: () -> Unit,
    onRemove: () -> Unit,
    isDeleteEnabled: Boolean
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = document.displayName.substringBeforeLast('.', document.displayName),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onRemove,
            enabled = isDeleteEnabled,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = stringResource(R.string.delete)
            )
        }
    }
}
