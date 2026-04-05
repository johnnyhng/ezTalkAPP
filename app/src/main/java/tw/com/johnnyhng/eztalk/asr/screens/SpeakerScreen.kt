package tw.com.johnnyhng.eztalk.asr.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tw.com.johnnyhng.eztalk.asr.R

@Composable
fun SpeakerScreen() {
    var directories by remember { mutableStateOf(sampleSpeakerDirectories()) }
    var selectedDocumentId by remember { mutableStateOf(directories.firstOrNull()?.documents?.firstOrNull()?.id) }
    val selectedDocument = directories
        .flatMap { it.documents }
        .firstOrNull { it.id == selectedDocumentId }

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
            text = stringResource(R.string.speaker_placeholder_description),
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
                SpeakerOverviewHeader()
                SpeakerDivider()
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
                            onDocumentSelected = { documentId ->
                                selectedDocumentId = documentId
                            }
                        )
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
                SpeakerPlaybackHeader(selectedDocument = selectedDocument)
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
private fun SpeakerDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun SpeakerOverviewHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = stringResource(R.string.speaker_overview_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.speaker_overview_subtitle),
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = { }) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.speaker_add_folder)
            )
        }
    }
}

@Composable
private fun SpeakerDirectorySection(
    directory: SpeakerDirectoryUi,
    selectedDocumentId: String?,
    onToggleExpand: () -> Unit,
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
                modifier = Modifier.padding(start = 12.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.speaker_refresh_folder)
                )
            }
            IconButton(
                onClick = { },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.speaker_remove_folder)
                )
            }
        }

        if (directory.isExpanded) {
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
            text = document.previewText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SpeakerPlaybackHeader(selectedDocument: SpeakerDocumentUi?) {
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
            contentDescription = stringResource(R.string.play)
        )
        SpeakerPlaybackAction(
            icon = Icons.Filled.Pause,
            contentDescription = stringResource(R.string.speaker_pause)
        )
        SpeakerPlaybackAction(
            icon = Icons.Filled.Stop,
            contentDescription = stringResource(R.string.stop)
        )
    }
}

@Composable
private fun SpeakerPlaybackAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    IconButton(onClick = { }) {
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

private fun sampleSpeakerDirectories(): List<SpeakerDirectoryUi> {
    return listOf(
        SpeakerDirectoryUi(
            id = "dir-1",
            displayName = "產品簡報",
            isExpanded = true,
            documents = listOf(
                SpeakerDocumentUi(
                    id = "doc-1",
                    displayName = "開場介紹.txt",
                    previewText = "大家好，今天我想先用三分鐘介紹這次專案的核心價值。",
                    fullText = "大家好，今天我想先用三分鐘介紹這次專案的核心價值。\n\n我們的目標不是只做出功能，而是讓語音與文本操作真正能在實際場景中穩定共存。這個演講者模式會先從本地 txt 播放開始，再逐步接入語音選檔與模型判斷。"
                ),
                SpeakerDocumentUi(
                    id = "doc-2",
                    displayName = "產品優勢.txt",
                    previewText = "我們把辨識、文本管理與播放控制整合在同一個操作面板。",
                    fullText = "我們把辨識、文本管理與播放控制整合在同一個操作面板。\n\n使用者可以先快速總覽資料夾中的文本，再選定內容進行朗讀。後續若 ASR 成熟，也可以在同一個頁面用語音直接指定要播放的檔案。"
                )
            )
        ),
        SpeakerDirectoryUi(
            id = "dir-2",
            displayName = "教學講稿",
            isExpanded = false,
            documents = listOf(
                SpeakerDocumentUi(
                    id = "doc-3",
                    displayName = "課程一.txt",
                    previewText = "第一堂課先說明環境配置與基本操作。",
                    fullText = "第一堂課先說明環境配置與基本操作。\n\n這裡是教學講稿的示意內容，後續會改成從使用者選取的本地資料夾載入。"
                )
            )
        )
    )
}
