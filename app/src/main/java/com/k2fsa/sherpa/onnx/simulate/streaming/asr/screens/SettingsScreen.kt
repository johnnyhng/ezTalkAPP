package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.widgets.RemoteModelsManager
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val userSettings by homeViewModel.userSettings.collectAsState()
    var showUserIdDialog by remember { mutableStateOf(false) }
    val showRemoteModelsDialog by homeViewModel.showRemoteModelsDialog.collectAsState()

    val models = homeViewModel.models
    val selectedModel = homeViewModel.selectedModel
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var modelUrl by remember(userSettings.modelUrl) { mutableStateOf(userSettings.modelUrl) }
    var feedbackUrl by remember(userSettings.feedbackUrl) { mutableStateOf(userSettings.feedbackUrl) }
    val isDownloading = homeViewModel.isDownloading
    val downloadProgress = homeViewModel.downloadProgress
    val canDeleteModel = homeViewModel.canDeleteModel

    LaunchedEffect(Unit) {
        homeViewModel.downloadEventFlow.collectLatest { event ->
            when (event) {
                is DownloadUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showUserIdDialog) {
        UserIdDialog(
            currentUserId = userSettings.userId,
            onDismiss = { showUserIdDialog = false },
            onConfirm = { newUserId ->
                homeViewModel.updateUserId(newUserId)
                showUserIdDialog = false
            }
        )
    }

    if (showRemoteModelsDialog) {
        RemoteModelsManager(homeViewModel = homeViewModel)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        // User ID setting
        Button(onClick = { showUserIdDialog = true }, enabled = !isDownloading) {
            Text(text = "Edit User ID")
        }
        Text(text = "Current User ID: ${userSettings.userId}")

        // Model Selection
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text("ASR Model", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
                expanded = modelMenuExpanded,
                onExpandedChange = {
                    if (!isDownloading) {
                        modelMenuExpanded = !modelMenuExpanded
                    }
                },
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = selectedModel?.name ?: "No model selected",
                    onValueChange = {},
                    label = { Text("Selected Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                    enabled = !isDownloading
                )
                ExposedDropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false },
                    modifier = Modifier.exposedDropdownSize()
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.name) },
                            onClick = {
                                homeViewModel.updateModelName(model.name)
                                modelMenuExpanded = false
                            },
                            leadingIcon = {
                                RadioButton(
                                    selected = (model.name == selectedModel?.name),
                                    onClick = null
                                )
                            }
                        )
                    }
                    if (models.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No models found") },
                            enabled = false,
                            onClick = {}
                        )
                    }
                }
            }
            OutlinedTextField(
                value = modelUrl,
                onValueChange = { 
                    modelUrl = it
                    homeViewModel.updateModelUrl(it)
                },
                label = { Text("Model Download URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isDownloading
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = {
                    homeViewModel.showRemoteModelsDialog()
                }, enabled = !isDownloading && modelUrl.isNotBlank()) {
                    Icon(Icons.Default.Cloud, contentDescription = "Check version")
                }
                IconButton(onClick = {
                    selectedModel?.let {
                        homeViewModel.deleteModel(it)
                    }
                }, enabled = !isDownloading && canDeleteModel) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete model")
                }
            }
            if (isDownloading) {
                if (downloadProgress != null) {
                    LinearProgressIndicator(progress = downloadProgress, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        OutlinedTextField(
            value = feedbackUrl,
            onValueChange = {
                feedbackUrl = it
                homeViewModel.updateFeedbackUrl(it)
            },
            label = { Text("Feedback URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isDownloading
        )

        // Delay Slider
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = "Delay: ${userSettings.lingerMs.roundToInt()} ms",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Slider(
                value = userSettings.lingerMs,
                onValueChange = { homeViewModel.updateLingerMs(it) },
                valueRange = 0f..10000f,
                steps = ((10000f - 0f) / 100f).toInt() - 1,
                enabled = !isDownloading
            )
        }

        // Recognize Time Slider
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = "Recognize Time: ${userSettings.partialIntervalMs.roundToInt()} ms",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Slider(
                value = userSettings.partialIntervalMs,
                onValueChange = { homeViewModel.updatePartialIntervalMs(it) },
                valueRange = 200f..1000f,
                steps = ((1000f - 200f) / 50f).toInt() - 1,
                enabled = !isDownloading
            )
        }

        // Save Mode Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Save VAD Segments")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = !userSettings.saveVadSegmentsOnly,
                onCheckedChange = { isChecked -> homeViewModel.updateSaveVadSegmentsOnly(!isChecked) },
                enabled = !isDownloading
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Save Full Audio")
        }
    }
}

@Composable
private fun UserIdDialog(
    currentUserId: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var userId by remember { mutableStateOf(currentUserId) }
    var isValid by remember { mutableStateOf(userId.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(userId).matches()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit User ID") },
        text = {
            Column {
                OutlinedTextField(
                    value = userId,
                    onValueChange = {
                        userId = it
                        isValid = it.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(it).matches()
                    },
                    label = { Text("User ID (email format)") },
                    isError = !isValid,
                    singleLine = true
                )
                if (!isValid && userId.isNotEmpty()) {
                    Text(
                        text = "Please enter a valid email address.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(userId) },
                enabled = isValid
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
