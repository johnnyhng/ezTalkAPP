package tw.com.johnnyhng.eztalk.asr.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel

@Composable
fun RemoteModelsManager(
    homeViewModel: HomeViewModel,
) {
    val remoteModels = homeViewModel.remoteModels
    val remoteModelsErrorMessage = homeViewModel.remoteModelsErrorMessage
    var selectedModel by remember { mutableStateOf(remoteModels.firstOrNull()) }
    val isDownloading by homeViewModel.isDownloadingFlow.collectAsState()
    val downloadProgress by homeViewModel.downloadProgressFlow.collectAsState()

    LaunchedEffect(remoteModels) {
        if (selectedModel == null || remoteModels.none { it.name == selectedModel?.name }) {
            selectedModel = remoteModels.firstOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                homeViewModel.dismissRemoteModelsDialog()
            }
        },
        title = { Text(stringResource(R.string.available_remote_models)) },
        text = {
            Column {
                if (homeViewModel.isFetchingRemoteModels) {
                    CircularProgressIndicator()
                } else if (remoteModels.isEmpty() && remoteModelsErrorMessage.isNullOrBlank()) {
                    Text(stringResource(R.string.no_remote_models_found))
                } else if (remoteModels.isNotEmpty()) {
                    LazyColumn {
                        items(remoteModels) { model ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isDownloading) { selectedModel = model }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = (model == selectedModel),
                                    onClick = { selectedModel = model },
                                    enabled = !isDownloading
                                )
                                Text(
                                    text = model.name,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                                if (model.updateAvailable) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdateAlt,
                                        contentDescription = stringResource(R.string.update_available),
                                        tint = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }

                if (!remoteModelsErrorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = remoteModelsErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedModel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isDownloading) {
                            val progress = downloadProgress
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.weight(1f))
                            }
                        }

                        IconButton(
                            onClick = {
                                selectedModel?.let {
                                    homeViewModel.downloadModel(it)
                                }
                            },
                            enabled = !isDownloading
                        ) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = { homeViewModel.dismissRemoteModelsDialog() },
                enabled = !isDownloading
            ) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
