package tw.com.johnnyhng.eztalk.asr.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.experiment.ExperimentSuggestionMode
import tw.com.johnnyhng.eztalk.asr.experiment.ExperimentViewModel
import tw.com.johnnyhng.eztalk.asr.experiment.ExperimentViewModelFactory
import tw.com.johnnyhng.eztalk.asr.experiment.ZhuyinSuggestionModule
import tw.com.johnnyhng.eztalk.asr.experiment.stripPrefix
import tw.com.johnnyhng.eztalk.asr.experiment.traditionalChineseEmotions
import tw.com.johnnyhng.eztalk.asr.experiment.traditionalChineseInitialPhrases
import tw.com.johnnyhng.eztalk.asr.experiment.zhuyinSingleRowKeyGroups
import tw.com.johnnyhng.eztalk.asr.llm.TranscriptCorrectionProviderFactory
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExperimentScreen(
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val userSettings by homeViewModel.userSettings.collectAsState()
    val appContext = context.applicationContext
    val geminiModel = userSettings.geminiModel
    val viewModelFactory = remember(appContext, geminiModel) {
        val provider = TranscriptCorrectionProviderFactory(appContext).create(geminiModel)
        ExperimentViewModelFactory(
            ZhuyinSuggestionModule(
                llmProvider = provider,
                llmModel = geminiModel
            )
        )
    }
    val experimentViewModel: ExperimentViewModel = viewModel(
        key = "experiment-$geminiModel",
        factory = viewModelFactory
    )
    val uiState by experimentViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Canvas: Text Area (Full Width)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = uiState.inputText.ifBlank { " " },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = experimentViewModel::backspace) {
                        Icon(
                            Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = stringResource(R.string.experiment_backspace)
                        )
                    }
                    IconButton(onClick = experimentViewModel::clear) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.clear)
                        )
                    }
                }
            }
        }

        // 2. Scrollable Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Merged Section: Word Candidates or Initial Phrases
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (uiState.inputText.isBlank()) {
                            stringResource(R.string.experiment_initial_phrases)
                        } else {
                            stringResource(R.string.experiment_word_candidates)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (uiState.isThinking) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.inputText.isBlank()) {
                        traditionalChineseInitialPhrases.forEach { phrase ->
                            ElevatedButton(onClick = { experimentViewModel.inputInitialPhrase(phrase) }) {
                                Text(phrase, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    } else {
                        uiState.wordCandidates.forEach { candidate ->
                            ElevatedButton(
                                onClick = { experimentViewModel.applyCandidate(candidate) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(candidate, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }

            // Sentence Candidates
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.experiment_sentence_candidates),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (uiState.isSentenceThinking) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = experimentViewModel::requestSentenceSuggestions,
                        enabled = !uiState.isLoading && uiState.inputText.isNotBlank()
                    ) {
                        Text(stringResource(R.string.experiment_sentence_candidates))
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.sentenceCandidates.forEach { candidate ->
                        Surface(
                            onClick = { experimentViewModel.applyCandidate(candidate) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = stripPrefix(uiState.inputText, candidate),
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
