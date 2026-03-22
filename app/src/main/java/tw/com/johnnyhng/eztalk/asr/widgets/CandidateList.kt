package tw.com.johnnyhng.eztalk.asr.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.data.classes.Transcript

@Composable
fun CandidateList(
    modifier: Modifier = Modifier,
    resultList: List<Transcript>,
    lazyListState: LazyListState,
    isEditing: Boolean,
    editingIndex: Int,
    editingText: String,
    onEditingTextChange: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onConfirmEdit: (Int, String) -> Unit,
    onItemClick: (Int, Transcript) -> Unit,
    // Item Actions
    onTtsClick: (Int, String) -> Unit,
    onPlayClick: (String) -> Unit,
    onDeleteClick: (Int, String) -> Unit,
    // Recognition states
    isRecognizingSpeech: Boolean,
    currentlyPlaying: String?,
    isStarted: Boolean,
    isTtsSpeaking: Boolean,
    countdownProgress: Float,
    isDataCollectMode: Boolean,
    // Editing candidates
    localCandidate: String?,
    isFetchingCandidates: Boolean,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        state = lazyListState
    ) {
        itemsIndexed(
            resultList,
            key = { _, item -> item.wavFilePath.ifEmpty { item.hashCode() } }
        ) { index, result ->
            if (isEditing && editingIndex == index) {
                CandidateEditRow(
                    text = editingText,
                    onTextChange = onEditingTextChange,
                    menuItems = (listOfNotNull(
                        result.recognizedText,
                        result.modifiedText,
                        localCandidate
                    ) + result.remoteCandidates).distinct(),
                    isFetching = isFetchingCandidates,
                    onCancel = onCancelEdit,
                    onConfirm = { onConfirmEdit(index, editingText) }
                )
            } else {
                CandidateItemRow(
                    index = index,
                    transcript = result,
                    isLastItem = index == resultList.size - 1,
                    isRecognizingSpeech = isRecognizingSpeech,
                    countdownProgress = countdownProgress,
                    isStarted = isStarted,
                    currentlyPlaying = currentlyPlaying,
                    isTtsSpeaking = isTtsSpeaking,
                    isEditing = isEditing,
                    isDataCollectMode = isDataCollectMode,
                    onClick = { if (result.mutable) onItemClick(index, result) },
                    onTtsClick = { onTtsClick(index, result.modifiedText) },
                    onPlayClick = { onPlayClick(result.wavFilePath) },
                    onDeleteClick = { onDeleteClick(index, result.wavFilePath) }
                )
            }
        }
    }
}

@Composable
fun CandidateEditRow(
    text: String,
    onTextChange: (String) -> Unit,
    menuItems: List<String>,
    isFetching: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        EditableDropdown(
            value = text,
            onValueChange = onTextChange,
            label = { Text(stringResource(id = R.string.edit)) },
            menuItems = menuItems,
            isRecognizing = isFetching,
            modifier = Modifier.weight(1f),
            startExpanded = true
        )
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(id = R.string.cancel_edit)
            )
        }
        IconButton(onClick = onConfirm) {
            Icon(
                Icons.Default.RecordVoiceOver,
                contentDescription = stringResource(id = R.string.confirm_edit)
            )
        }
    }
}

@Composable
fun CandidateItemRow(
    index: Int,
    transcript: Transcript,
    isLastItem: Boolean,
    isRecognizingSpeech: Boolean,
    countdownProgress: Float,
    isStarted: Boolean,
    currentlyPlaying: String?,
    isTtsSpeaking: Boolean,
    isEditing: Boolean,
    isDataCollectMode: Boolean,
    onClick: () -> Unit,
    onTtsClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = !isRecognizingSpeech && !isEditing && !isStarted && transcript.mutable,
                onClick = onClick
            )
    ) {
        Text(
            text = "${index + 1}: ${transcript.modifiedText}",
            modifier = Modifier.weight(1f)
        )

        if (isLastItem && isRecognizingSpeech && countdownProgress > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                progress = countdownProgress,
                modifier = Modifier.size(24.dp)
            )
        }

        if (transcript.wavFilePath.isNotEmpty()) {
            if (!isDataCollectMode) {
                IconButton(
                    onClick = onTtsClick,
                    enabled = !isStarted && currentlyPlaying == null && !isTtsSpeaking && !isEditing
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = stringResource(id = R.string.talk)
                    )
                }
            }

            IconButton(
                onClick = onPlayClick,
                enabled = !isStarted && !isTtsSpeaking && (currentlyPlaying == null || currentlyPlaying == transcript.wavFilePath) && !isEditing
            ) {
                Icon(
                    imageVector = if (currentlyPlaying == transcript.wavFilePath) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (currentlyPlaying == transcript.wavFilePath) stringResource(id = R.string.stop) else stringResource(id = R.string.play)
                )
            }

            IconButton(
                onClick = onDeleteClick,
                enabled = !isStarted && !isTtsSpeaking && currentlyPlaying == null && !isEditing
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(id = R.string.delete)
                )
            }
        }
    }
}
