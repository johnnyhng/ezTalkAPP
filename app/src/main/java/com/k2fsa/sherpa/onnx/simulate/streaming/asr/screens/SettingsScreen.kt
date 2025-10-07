package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

import android.util.Patterns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel = viewModel(),
) {
    val userSettings by homeViewModel.userSettings.collectAsState()
    var showUserIdDialog by remember { mutableStateOf(false) }

    if (showUserIdDialog) {
        UserIdDialog(
            currentUserId = userSettings.userId ?: "",
            onDismiss = { showUserIdDialog = false },
            onConfirm = { newUserId ->
                homeViewModel.updateUserId(newUserId)
                showUserIdDialog = false
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        // User ID setting
        Button(onClick = { showUserIdDialog = true }) {
            Text(text = "Edit User ID")
        }
        Text(text = "Current User ID: ${userSettings.userId ?: "Not set"}")

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
                steps = ((10000f - 0f) / 100f).toInt() - 1
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
                steps = ((1000f - 200f) / 50f).toInt() - 1
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
                onCheckedChange = { isChecked -> homeViewModel.updateSaveVadSegmentsOnly(!isChecked) }
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
