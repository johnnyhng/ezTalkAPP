package tw.com.johnnyhng.eztalk.asr.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.audio.rememberSpeechOutputController
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
    val (speechController, _) = rememberSpeechOutputController()

    Box(modifier = Modifier.fillMaxSize()) {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.inputText.ifBlank { " " },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { speechController.speak(uiState.inputText) },
                            enabled = uiState.inputText.isNotBlank()
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.playback)
                            )
                        }
                        IconButton(
                            onClick = experimentViewModel::requestSentenceSuggestions,
                            enabled = !uiState.isLoading && uiState.inputText.isNotBlank()
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.experiment_sentence_candidates)
                            )
                        }
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
                // Word Candidates or Initial Phrases
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.inputText.isBlank()) {
                        traditionalChineseInitialPhrases.forEach { phrase ->
                            ElevatedButton(
                                onClick = { experimentViewModel.inputInitialPhrase(phrase) },
                                modifier = Modifier.heightIn(min = 64.dp),
                                colors = androidx.compose.material3.ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text(phrase, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    } else {
                        uiState.wordCandidates.forEach { candidate ->
                            ElevatedButton(
                                onClick = { experimentViewModel.applyCandidate(candidate) },
                                modifier = Modifier.heightIn(min = 64.dp),
                                colors = androidx.compose.material3.ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text(candidate, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }

                // Sentence Candidates
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.sentenceCandidates.forEach { candidate ->
                        Surface(
                            onClick = { experimentViewModel.applyCandidate(candidate) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = stripPrefix(uiState.inputText, candidate),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Global Loading Overlay
        if (uiState.isLoading) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}
