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
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tw.com.johnnyhng.eztalk.asr.R

@Composable
fun DataCollectWidget(
    text: String,
    onTextChange: (String) -> Unit,
    onTtsClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSequenceMode: Boolean,
    onSequenceModeChange: (Boolean) -> Unit,
    onUploadClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isPreviousEnabled: Boolean,
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
            Text(stringResource(id = R.string.sequence_mode))
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isSequenceMode,
                onCheckedChange = onSequenceModeChange,
                enabled = isSequenceModeSwitchEnabled
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onUploadClick) {
                Text(stringResource(id = R.string.upload_txt))
            }
        }
        if (showNoQueueMessage) {
            Text(
                stringResource(id = R.string.please_upload_txt),
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
                    label = { Text(stringResource(id = R.string.text_for_recording)) },
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(onClick = onTtsClick) {
                Icon(
                    Icons.Default.RecordVoiceOver,
                    contentDescription = stringResource(id = R.string.tts_for_data_collection)
                )
            }
            if (isSequenceMode) {
                IconButton(onClick = onPreviousClick, enabled = isPreviousEnabled) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(id = R.string.previous)
                    )
                }
                IconButton(onClick = onNextClick, enabled = isNextEnabled) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = stringResource(id = R.string.next)
                    )
                }
            } else {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(id = R.string.clear_text)
                    )
                }
            }
        }
    }
}
