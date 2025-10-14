package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RemoteModelsManager(
    homeViewModel: HomeViewModel,
) {
    val remoteModels = homeViewModel.remoteModels
    var selectedModel by remember { mutableStateOf(remoteModels.firstOrNull()) }
    val isDownloading = homeViewModel.isDownloading
    val downloadProgress = homeViewModel.downloadProgress

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                homeViewModel.dismissRemoteModelsDialog()
            }
        },
        title = { Text("Available Remote Models") },
        text = {
            Column {
                if (homeViewModel.isFetchingRemoteModels) {
                    CircularProgressIndicator()
                } else if (remoteModels.isEmpty()) {
                    Text("No remote models found.")
                } else {
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
                                    text = model,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Show download controls or progress bar
                if (isDownloading) {
                    // Show progress bar only when downloading
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = downloadProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                } else if (selectedModel != null) {
                    // Show download button next to selected model text when not downloading
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Download: ${selectedModel!!}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(
                            onClick = {
                                selectedModel?.let {
                                    homeViewModel.downloadModel(it)
                                }
                            },
                            enabled = true // Already covered by the 'else if'
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download")
                        }
                    }
                }
            }
        },
        confirmButton = {
            // The confirm button is moved inside the `text` lambda,
            // so we can leave this empty or use it for another action if needed.
        },
        dismissButton = {
            TextButton(
                onClick = { homeViewModel.dismissRemoteModelsDialog() },
                enabled = !isDownloading
            ) {
                Text("Close")
            }
        }
    )
}
