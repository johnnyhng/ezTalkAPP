package tw.com.johnnyhng.eztalk.asr.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.com.johnnyhng.eztalk.asr.managers.DataCollectViewModel
import java.util.Locale
import tw.com.johnnyhng.eztalk.asr.R

@Composable
fun DataCollectScreen(
    dataCollectViewModel: DataCollectViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by dataCollectViewModel.uiState.collectAsState()
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.TRADITIONAL_CHINESE
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        dataCollectViewModel.importFromUri(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.data_collect_page_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                DataCollectWidget(
                    text = uiState.text,
                    onTextChange = dataCollectViewModel::onTextChange,
                    onTtsClick = {
                        if (uiState.text.isNotBlank()) {
                            tts?.speak(uiState.text, TextToSpeech.QUEUE_FLUSH, null, "data_collect_tts")
                        }
                    },
                    isSequenceMode = uiState.isSequenceMode,
                    onSequenceModeChange = dataCollectViewModel::onSequenceModeChange,
                    onUploadClick = { filePickerLauncher.launch("text/*") },
                    onPreviousClick = dataCollectViewModel::moveToPrevious,
                    onNextClick = dataCollectViewModel::moveToNext,
                    onDeleteClick = dataCollectViewModel::clearText,
                    isPreviousEnabled = uiState.previousCount > 0,
                    isNextEnabled = uiState.remainingCount > 0,
                    isSequenceModeSwitchEnabled = true,
                    showNoQueueMessage = uiState.showNoQueueMessage
                )
            }
        }

        Text(
            text = stringResource(
                R.string.data_collect_queue_status,
                uiState.previousCount,
                uiState.remainingCount
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.data_collect_page_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}
