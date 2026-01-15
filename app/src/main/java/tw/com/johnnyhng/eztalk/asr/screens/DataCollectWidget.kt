package tw.com.johnnyhng.eztalk.asr.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DataCollectWidget(
    text: String,
    onTextChange: (String) -> Unit,
    onTtsClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSequenceMode: Boolean,
    onSequenceModeChange: (Boolean) -> Unit,
    onUploadClick: () -> Unit,
    onNextClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isNextEnabled: Boolean,
    isSequenceModeSwitchEnabled: Boolean,
    showNoQueueMessage: Boolean
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sequence Mode")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isSequenceMode,
                onCheckedChange = onSequenceModeChange,
                enabled = isSequenceModeSwitchEnabled
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onUploadClick) {
                Text("Upload TXT")
            }
        }
        if (showNoQueueMessage) {
            Text(
                "Please upload a TXT file to use Sequence Mode.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            if (isSequenceMode) {
                Text(
                    text = text,
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text("Text for recording") },
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(onClick = onTtsClick) {
                Icon(
                    Icons.Default.RecordVoiceOver,
                    contentDescription = "TTS for data collection text"
                )
            }
            if (isSequenceMode) {
                IconButton(onClick = onNextClick, enabled = isNextEnabled) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next"
                    )
                }
            } else {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Clear text"
                    )
                }
            }
        }
    }
}
