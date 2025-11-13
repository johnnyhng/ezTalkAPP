package tw.com.johnnyhng.eztalk.asr.screens

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.managers.DownloadUiEvent
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.widgets.RemoteModelsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context as Activity
    val coroutineScope = rememberCoroutineScope()
    val userSettings by homeViewModel.userSettings.collectAsState()
    val showRemoteModelsDialog by homeViewModel.showRemoteModelsDialog.collectAsState()

    val models = homeViewModel.models
    val selectedModel = homeViewModel.selectedModel
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var modelUrl by remember(userSettings.modelUrl) { mutableStateOf(userSettings.modelUrl) }
    var backendUrl by remember(userSettings.backendUrl) { mutableStateOf(userSettings.backendUrl) }
    val isDownloading = homeViewModel.isDownloading
    val downloadProgress = homeViewModel.downloadProgress
    val canDeleteModel = homeViewModel.canDeleteModel

    var languageMenuExpanded by remember { mutableStateOf(false) }
    val languages = listOf("en" to "English", "zh" to "Chinese")

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.email?.let {
                    homeViewModel.updateUserId(it)
                    Toast.makeText(context, "Signed in as $it", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
                Toast.makeText(context, "Google sign in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.downloadEventFlow.collectLatest { event ->
            when (event) {
                is DownloadUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showRemoteModelsDialog) {
        RemoteModelsManager(homeViewModel = homeViewModel)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        // Language selection
        ExposedDropdownMenuBox(
            expanded = languageMenuExpanded,
            onExpandedChange = { languageMenuExpanded = !languageMenuExpanded },
        ) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = Locale(userSettings.language).displayName,
                onValueChange = {},
                label = { Text("Language") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded) },
            )
            ExposedDropdownMenu(
                expanded = languageMenuExpanded,
                onDismissRequest = { languageMenuExpanded = false },
            ) {
                languages.forEach { (code, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            coroutineScope.launch {
                                homeViewModel.updateLanguage(code)
                                languageMenuExpanded = false
                                activity.recreate()
                            }
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // User ID setting
        Text(text = "Current User ID: ${userSettings.userId}")
        Row {
            Button(onClick = { launcher.launch(googleSignInClient.signInIntent) }, enabled = !isDownloading) {
                Text(text = "Sign in with Google")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = {
                googleSignInClient.signOut().addOnCompleteListener {
                    homeViewModel.updateUserId("user@example.com") // Reset to default
                    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                }
            }, enabled = !isDownloading) {
                Text("Sign Out")
            }
        }

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
            value = backendUrl,
            onValueChange = {
                backendUrl = it
                homeViewModel.updateBackendUrl(it)
            },
            label = { Text("backend URL") },
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

        // Inline Edit Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Inline Edit")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = userSettings.inlineEdit,
                onCheckedChange = { homeViewModel.updateInlineEdit(it) },
                enabled = !isDownloading
            )
        }
    }
}
