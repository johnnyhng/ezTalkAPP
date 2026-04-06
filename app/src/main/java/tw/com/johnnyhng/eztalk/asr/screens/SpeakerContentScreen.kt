package tw.com.johnnyhng.eztalk.asr.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tw.com.johnnyhng.eztalk.asr.R

@Composable
internal fun SpeakerContentScreen(
    selectedDocument: SpeakerDocumentUi?,
    isTtsReady: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    isEditing: Boolean,
    editingText: String,
    onEditingTextChange: (String) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SpeakerPlaybackHeader(
                selectedDocument = selectedDocument,
                isTtsReady = isTtsReady,
                isPlaying = isPlaying,
                isPaused = isPaused,
                isEditing = isEditing,
                onPlay = onPlay,
                onPause = onPause,
                onStop = onStop,
                onEdit = onEdit,
                onSave = onSave,
                onCancelEdit = onCancelEdit
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
                } else if (isEditing) {
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = onEditingTextChange,
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = selectedDocument.fullText,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakerPlaybackHeader(
    selectedDocument: SpeakerDocumentUi?,
    isTtsReady: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    isEditing: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit
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
        }
        if (isEditing) {
            SpeakerPlaybackAction(
                icon = Icons.Filled.Save,
                contentDescription = stringResource(R.string.confirm_edit),
                enabled = selectedDocument != null,
                onClick = onSave
            )
            TextButton(
                onClick = onCancelEdit,
                enabled = selectedDocument != null
            ) {
                Text(text = stringResource(R.string.cancel_edit))
            }
            return
        }
        SpeakerPlaybackAction(
            icon = Icons.Filled.Edit,
            contentDescription = stringResource(R.string.edit),
            enabled = selectedDocument != null,
            onClick = onEdit
        )
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
    icon: ImageVector,
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
