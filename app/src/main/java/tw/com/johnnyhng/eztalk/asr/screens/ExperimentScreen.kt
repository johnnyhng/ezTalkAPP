package tw.com.johnnyhng.eztalk.asr.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
    var selectedKeyGroupLabel by rememberSaveable {
        androidx.compose.runtime.mutableStateOf(zhuyinSingleRowKeyGroups.first().label)
    }
    val selectedKeyGroup = remember(selectedKeyGroupLabel) {
        zhuyinSingleRowKeyGroups.firstOrNull { it.label == selectedKeyGroupLabel }
            ?: zhuyinSingleRowKeyGroups.first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = uiState.inputText.ifBlank { " " },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.width(220.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.experiment_initial_phrases), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    traditionalChineseInitialPhrases.forEach { phrase ->
                        OutlinedButton(onClick = { experimentViewModel.inputInitialPhrase(phrase) }) {
                            Text(phrase)
                        }
                    }
                }

                Text(stringResource(R.string.experiment_emotion), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    traditionalChineseEmotions.forEach { emotion ->
                        FilterChip(
                            selected = uiState.selectedEmotion == emotion,
                            onClick = { experimentViewModel.selectEmotion(emotion) },
                            label = { Text("${emotion.emoji} ${emotion.label}") }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = experimentViewModel::requestWordSuggestions,
                        enabled = !uiState.isLoading && uiState.inputText.isNotBlank()
                    ) {
                        Text(stringResource(R.string.experiment_word_candidates))
                    }
                    Button(
                        onClick = experimentViewModel::requestSentenceSuggestions,
                        enabled = !uiState.isLoading && uiState.inputText.isNotBlank()
                    ) {
                        Text(stringResource(R.string.experiment_sentence_candidates))
                    }
                }

                if (uiState.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                uiState.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = when (uiState.suggestionMode) {
                        ExperimentSuggestionMode.WORD -> stringResource(R.string.experiment_word_candidates)
                        ExperimentSuggestionMode.SENTENCE -> stringResource(R.string.experiment_sentence_candidates)
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                if (uiState.hasRequestedSuggestions && !uiState.isLoading && uiState.candidates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.experiment_no_candidates),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.candidates.forEach { candidate ->
                            ElevatedButton(onClick = { experimentViewModel.applyCandidate(candidate) }) {
                                Text(candidate)
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                zhuyinSingleRowKeyGroups.forEach { group ->
                    FilterChip(
                        selected = selectedKeyGroupLabel == group.label,
                        onClick = { selectedKeyGroupLabel = group.label },
                        label = {
                            Text(
                                text = group.label,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedKeyGroup.rows.flatMap { row -> row.map { it.toString() } }.forEach { key ->
                    OutlinedButton(
                        onClick = { experimentViewModel.inputCharacter(key) },
                        modifier = Modifier.width(72.dp)
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
