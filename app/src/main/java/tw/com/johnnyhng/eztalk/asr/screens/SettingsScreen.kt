package tw.com.johnnyhng.eztalk.asr.screens

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.UserRecoverableAuthException
import tw.com.johnnyhng.eztalk.asr.NavRoutes
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.auth.GoogleAccountSession
import tw.com.johnnyhng.eztalk.asr.auth.GoogleSignInManager
import tw.com.johnnyhng.eztalk.asr.llm.GoogleAuthGeminiAccessTokenProvider
import tw.com.johnnyhng.eztalk.asr.managers.DownloadUiEvent
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.widgets.RemoteModelsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface GeminiAuthStatus {
    data object NotSignedIn : GeminiAuthStatus
    data object Checking : GeminiAuthStatus
    data object Ready : GeminiAuthStatus
    data class Error(val message: String) : GeminiAuthStatus
}

private fun geminiOAuthErrorMessage(
    context: android.content.Context,
    error: Throwable
): String {
    val details = buildString {
        append(error.message.orEmpty())
        val causeMessage = error.cause?.message.orEmpty()
        if (causeMessage.isNotBlank()) {
            append(' ')
            append(causeMessage)
        }
    }.lowercase()

    return when {
        "invalid_scope" in details -> context.getString(R.string.gemini_oauth_status_invalid_scope)
        else -> error.message ?: context.getString(R.string.gemini_oauth_status_unknown_error)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val userSettings by homeViewModel.userSettings.collectAsState()
    val showRemoteModelsDialog by homeViewModel.showRemoteModelsDialog.collectAsState()
    val scope = rememberCoroutineScope()

    val models = homeViewModel.models
    val selectedModel = homeViewModel.selectedModel
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var entryScreenMenuExpanded by remember { mutableStateOf(false) }
    var geminiModelMenuExpanded by remember { mutableStateOf(false) }
    var backendUrl by remember(userSettings.backendUrl) { mutableStateOf(userSettings.backendUrl) }
    val geminiModelOptions = listOf(
        "none" to context.getString(R.string.gemini_model_option_none),
        "gemini-2.5-flash" to context.getString(R.string.gemini_model_option_flash_default)
    )
    val selectedGeminiModelLabel = geminiModelOptions
        .firstOrNull { it.first == userSettings.geminiModel }
        ?.second
        ?: userSettings.geminiModel
    val isDownloading by homeViewModel.isDownloadingFlow.collectAsState()
    val downloadProgress by homeViewModel.downloadProgressFlow.collectAsState()
    val canDeleteModel = homeViewModel.canDeleteModel
    val entryScreenOptions = listOf(
        NavRoutes.Home.route to context.getString(R.string.home),
        NavRoutes.Translate.route to context.getString(R.string.translate),
        NavRoutes.Speaker.route to context.getString(R.string.speaker),
        NavRoutes.DataCollect.route to context.getString(R.string.data_collect)
    )
    val selectedEntryScreenLabel = entryScreenOptions
        .firstOrNull { it.first == userSettings.entryScreenRoute }
        ?.second
        ?: context.getString(R.string.home)

    val signInManager = remember { GoogleSignInManager() }
    val tokenProvider = remember(appContext) { GoogleAuthGeminiAccessTokenProvider(appContext) }
    var googleSession by remember { mutableStateOf<GoogleAccountSession?>(null) }
    var geminiAuthStatus by remember { mutableStateOf<GeminiAuthStatus>(GeminiAuthStatus.NotSignedIn) }
    lateinit var refreshGeminiAuthStatus: (GoogleAccountSession?) -> Unit

    val geminiConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refreshGeminiAuthStatus(googleSession)
        } else {
            geminiAuthStatus = GeminiAuthStatus.Error(
                context.getString(R.string.gemini_oauth_status_consent_denied)
            )
        }
    }

    fun launchGeminiRecovery(error: UserRecoverableAuthException) {
        geminiAuthStatus = GeminiAuthStatus.Error(
            context.getString(R.string.gemini_oauth_status_consent_required)
        )
        Toast.makeText(
            context,
            context.getString(R.string.gemini_oauth_consent_required),
            Toast.LENGTH_SHORT
        ).show()
        geminiConsentLauncher.launch(error.intent)
    }

    refreshGeminiAuthStatus = refresh@ { session ->
        if (session == null) {
            geminiAuthStatus = GeminiAuthStatus.NotSignedIn
            return@refresh
        }

        geminiAuthStatus = GeminiAuthStatus.Checking
        scope.launch {
            tokenProvider.fetchToken()
                .onSuccess {
                    geminiAuthStatus = GeminiAuthStatus.Ready
                }
                .onFailure { error ->
                    Log.w(TAG, "Gemini OAuth token check failed", error)
                    when (error) {
                        is UserRecoverableAuthException -> launchGeminiRecovery(error)
                        else -> {
                            geminiAuthStatus = GeminiAuthStatus.Error(
                                geminiOAuthErrorMessage(context, error)
                            )
                        }
                    }
                }
        }
    }

    LaunchedEffect(signInManager, context, userSettings.userId) {
        val session = signInManager.getCurrentSession(context)
        googleSession = session
        if (session != null && userSettings.userId != session.email) {
            homeViewModel.updateUserId(session.email)
        }
        refreshGeminiAuthStatus(session)
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

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        googleSession?.let { session ->
            Text(
                text = stringResource(
                    R.string.welcome_logged_in_user,
                    session.displayName?.takeIf { it.isNotBlank() } ?: session.email
                )
            )
            Text(
                text = when (val status = geminiAuthStatus) {
                    GeminiAuthStatus.NotSignedIn -> stringResource(R.string.gemini_oauth_status_not_signed_in)
                    GeminiAuthStatus.Checking -> stringResource(R.string.gemini_oauth_status_checking)
                    GeminiAuthStatus.Ready -> stringResource(R.string.gemini_oauth_status_ready)
                    is GeminiAuthStatus.Error -> stringResource(
                        R.string.gemini_oauth_status_error,
                        status.message
                    )
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { refreshGeminiAuthStatus(googleSession) },
                enabled = !isDownloading && geminiAuthStatus != GeminiAuthStatus.Checking
            ) {
                Text(stringResource(R.string.check_gemini_access))
            }
        }

        ExposedDropdownMenuBox(
            expanded = geminiModelMenuExpanded,
            onExpandedChange = {
                if (!isDownloading) {
                    geminiModelMenuExpanded = !geminiModelMenuExpanded
                }
            },
        ) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = selectedGeminiModelLabel,
                onValueChange = {},
                label = { Text(stringResource(R.string.gemini_model_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = geminiModelMenuExpanded) },
                enabled = !isDownloading
            )
            ExposedDropdownMenu(
                expanded = geminiModelMenuExpanded,
                onDismissRequest = { geminiModelMenuExpanded = false },
                modifier = Modifier.exposedDropdownSize()
            ) {
                geminiModelOptions.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            homeViewModel.updateGeminiModel(value)
                            geminiModelMenuExpanded = false
                        },
                        leadingIcon = {
                            RadioButton(
                                selected = value == userSettings.geminiModel,
                                onClick = null
                            )
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = backendUrl,
            onValueChange = {
                backendUrl = it
                homeViewModel.updateBackendUrl(it)
            },
            label = { Text(stringResource(R.string.backend_url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isDownloading
        )

        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(stringResource(R.string.entry_screen), style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
                expanded = entryScreenMenuExpanded,
                onExpandedChange = {
                    if (!isDownloading) {
                        entryScreenMenuExpanded = !entryScreenMenuExpanded
                    }
                },
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = selectedEntryScreenLabel,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.entry_screen)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = entryScreenMenuExpanded) },
                    enabled = !isDownloading
                )
                ExposedDropdownMenu(
                    expanded = entryScreenMenuExpanded,
                    onDismissRequest = { entryScreenMenuExpanded = false },
                    modifier = Modifier.exposedDropdownSize()
                ) {
                    entryScreenOptions.forEach { (route, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                homeViewModel.updateEntryScreenRoute(route)
                                entryScreenMenuExpanded = false
                            },
                            leadingIcon = {
                                RadioButton(
                                    selected = route == userSettings.entryScreenRoute,
                                    onClick = null
                                )
                            }
                        )
                    }
                }
            }
        }

        // Model Selection
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(stringResource(R.string.asr_model), style = MaterialTheme.typography.titleMedium)
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
                    value = selectedModel?.name ?: stringResource(R.string.no_model_selected),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.asr_model)) },
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
                                    selected = selectedModel?.name == model.name,
                                    onClick = null
                                )
                            }
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = {
                homeViewModel.showRemoteModelsDialog()
            }, enabled = !isDownloading && backendUrl.isNotBlank()) {
                Icon(Icons.Default.Cloud, contentDescription = stringResource(R.string.check_version))
            }
            IconButton(onClick = {
                selectedModel?.let(homeViewModel::deleteModel)
            }, enabled = !isDownloading && canDeleteModel) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_model))
            }
        }
        if (isDownloading) {
            val progress = downloadProgress
            if (progress != null) {
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        // Delay Slider
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.delay, userSettings.lingerMs.roundToInt()),
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
                text = stringResource(R.string.recognize_time, userSettings.partialIntervalMs.roundToInt()),
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
            Text(text = stringResource(R.string.save_vad_segments))
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = !userSettings.saveVadSegmentsOnly,
                onCheckedChange = { isChecked -> homeViewModel.updateSaveVadSegmentsOnly(!isChecked) },
                enabled = !isDownloading
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.save_full_audio))
        }

        // Inline Edit Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.inline_edit))
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = userSettings.inlineEdit,
                onCheckedChange = { homeViewModel.updateInlineEdit(it) },
                enabled = !isDownloading
            )
        }

        // TTS Feedback Switch (New)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.enable_tts_feedback))
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = userSettings.enableTtsFeedback,
                onCheckedChange = { homeViewModel.updateEnableTtsFeedback(it) },
                enabled = !isDownloading
            )
        }
    }
}
