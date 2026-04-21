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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import tw.com.johnnyhng.eztalk.asr.audio.AudioRouteDeviceUi
import tw.com.johnnyhng.eztalk.asr.auth.GoogleAccountSession
import tw.com.johnnyhng.eztalk.asr.auth.GoogleSignInManager
import tw.com.johnnyhng.eztalk.asr.auth.displayLabel
import tw.com.johnnyhng.eztalk.asr.llm.GoogleAuthGeminiAccessTokenProvider
import tw.com.johnnyhng.eztalk.asr.llm.SpeakerLlmExecutionMode
import tw.com.johnnyhng.eztalk.asr.llm.SpeakerLocalLlmAvailabilityChecker
import tw.com.johnnyhng.eztalk.asr.llm.SpeakerLocalLlmStatus
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

private fun localLlmStatusText(
    context: android.content.Context,
    status: SpeakerLocalLlmStatus
): String {
    return when (status) {
        SpeakerLocalLlmStatus.Checking -> context.getString(R.string.speaker_local_llm_status_checking)
        SpeakerLocalLlmStatus.Available -> context.getString(R.string.speaker_local_llm_status_available)
        SpeakerLocalLlmStatus.Downloadable -> context.getString(R.string.speaker_local_llm_status_downloadable)
        is SpeakerLocalLlmStatus.Downloading -> context.getString(R.string.speaker_local_llm_status_downloading)
        SpeakerLocalLlmStatus.Unavailable -> context.getString(R.string.speaker_local_llm_status_unavailable)
        is SpeakerLocalLlmStatus.Error -> context.getString(
            R.string.speaker_local_llm_status_error,
            status.message
        )
    }
}

private fun localLlmProgress(
    status: SpeakerLocalLlmStatus
): Float? {
    status as? SpeakerLocalLlmStatus.Downloading ?: return null
    val totalBytes = status.totalBytes ?: return null
    if (totalBytes <= 0L) return null
    val downloadedBytes = status.downloadedBytes ?: return null
    return (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

private fun localLlmProgressText(
    context: android.content.Context,
    status: SpeakerLocalLlmStatus
): String? {
    status as? SpeakerLocalLlmStatus.Downloading ?: return null
    val downloadedBytes = status.downloadedBytes ?: return null
    val totalBytes = status.totalBytes
    return if (totalBytes != null && totalBytes > 0L) {
        context.getString(
            R.string.speaker_local_llm_download_progress,
            downloadedBytes,
            totalBytes
        )
    } else {
        context.getString(
            R.string.speaker_local_llm_download_progress_indeterminate,
            downloadedBytes
        )
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
    val audioRoutingStatus by homeViewModel.audioRoutingStatus.collectAsState()
    val showRemoteModelsDialog by homeViewModel.showRemoteModelsDialog.collectAsState()
    val scope = rememberCoroutineScope()

    val models = homeViewModel.models
    val selectedModel = homeViewModel.selectedModel
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var entryScreenMenuExpanded by remember { mutableStateOf(false) }
    var geminiModelMenuExpanded by remember { mutableStateOf(false) }
    var speakerLlmModeMenuExpanded by remember { mutableStateOf(false) }
    var audioInputMenuExpanded by remember { mutableStateOf(false) }
    var audioOutputMenuExpanded by remember { mutableStateOf(false) }
    var advancedSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var backendUrl by remember(userSettings.backendUrl) { mutableStateOf(userSettings.backendUrl) }
    val geminiModelOptions = listOf(
        "none" to context.getString(R.string.gemini_model_option_none),
        "gemini-2.5-flash" to context.getString(R.string.gemini_model_option_flash_default)
    )
    val selectedGeminiModelLabel = geminiModelOptions
        .firstOrNull { it.first == userSettings.geminiModel }
        ?.second
        ?: userSettings.geminiModel
    val speakerLlmExecutionModeOptions = listOf(
        SpeakerLlmExecutionMode.AUTO_LOCAL.storageValue to context.getString(R.string.speaker_llm_execution_mode_auto_local),
        SpeakerLlmExecutionMode.CLOUD.storageValue to context.getString(R.string.speaker_llm_execution_mode_cloud)
    )
    val selectedSpeakerLlmExecutionModeLabel = speakerLlmExecutionModeOptions
        .firstOrNull { it.first == userSettings.speakerLlmExecutionMode }
        ?.second
        ?: userSettings.speakerLlmExecutionMode
    val isDownloading by homeViewModel.isDownloadingFlow.collectAsState()
    val downloadProgress by homeViewModel.downloadProgressFlow.collectAsState()
    val canDeleteModel = homeViewModel.canDeleteModel
    val entryScreenOptions = listOf(
        NavRoutes.Home.route to context.getString(R.string.home),
        NavRoutes.Translate.route to context.getString(R.string.translate),
        NavRoutes.Speaker.route to context.getString(R.string.speaker),
        NavRoutes.DataCollect.route to context.getString(R.string.data_collect),
        NavRoutes.Experiment.route to context.getString(R.string.experiment)
    )
    val selectedEntryScreenLabel = entryScreenOptions
        .firstOrNull { it.first == userSettings.entryScreenRoute }
        ?.second
        ?: context.getString(R.string.home)

    val signInManager = remember { GoogleSignInManager() }
    val tokenProvider = remember(appContext) { GoogleAuthGeminiAccessTokenProvider(appContext) }
    val localLlmAvailabilityChecker = remember(appContext) { SpeakerLocalLlmAvailabilityChecker(appContext) }
    var googleSession by remember { mutableStateOf<GoogleAccountSession?>(null) }
    var geminiAuthStatus by remember { mutableStateOf<GeminiAuthStatus>(GeminiAuthStatus.NotSignedIn) }
    var localLlmStatus by remember { mutableStateOf<SpeakerLocalLlmStatus>(SpeakerLocalLlmStatus.Checking) }
    var isLocalLlmDownloadRunning by remember { mutableStateOf(false) }
    lateinit var refreshGeminiAuthStatus: (GoogleAccountSession?) -> Unit
    lateinit var refreshLocalLlmStatus: () -> Unit
    lateinit var launchLocalLlmDownload: () -> Unit

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

    refreshLocalLlmStatus = refresh@{
        localLlmStatus = SpeakerLocalLlmStatus.Checking
        scope.launch {
            localLlmStatus = localLlmAvailabilityChecker.check()
        }
    }

    launchLocalLlmDownload = launch@{
        if (isLocalLlmDownloadRunning) return@launch
        isLocalLlmDownloadRunning = true
        scope.launch {
            localLlmStatus = SpeakerLocalLlmStatus.Downloading()
            val finalStatus = localLlmAvailabilityChecker.download { status ->
                localLlmStatus = status
            }
            localLlmStatus = finalStatus
            isLocalLlmDownloadRunning = false
            when (finalStatus) {
                SpeakerLocalLlmStatus.Available -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.speaker_local_llm_download_complete),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is SpeakerLocalLlmStatus.Error -> {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.speaker_local_llm_download_failed,
                            finalStatus.message
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> Unit
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
        refreshLocalLlmStatus()
    }

    LaunchedEffect(Unit) {
        homeViewModel.refreshAudioRoutingStatus()
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
                    session.displayLabel()
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
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.advanced_settings),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = { advancedSettingsExpanded = !advancedSettingsExpanded },
                    enabled = !isDownloading
                ) {
                    Icon(
                        imageVector = if (advancedSettingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.advanced_settings)
                    )
                }
            }

            if (advancedSettingsExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = stringResource(R.string.speaker_llm_execution_mode_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    ExposedDropdownMenuBox(
                        expanded = speakerLlmModeMenuExpanded,
                        onExpandedChange = {
                            if (!isDownloading) {
                                speakerLlmModeMenuExpanded = !speakerLlmModeMenuExpanded
                            }
                        }
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            readOnly = true,
                            value = selectedSpeakerLlmExecutionModeLabel,
                            onValueChange = {},
                            label = { Text(stringResource(R.string.speaker_llm_execution_mode_label)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = speakerLlmModeMenuExpanded)
                            },
                            enabled = !isDownloading
                        )
                        ExposedDropdownMenu(
                            expanded = speakerLlmModeMenuExpanded,
                            onDismissRequest = { speakerLlmModeMenuExpanded = false },
                            modifier = Modifier.exposedDropdownSize()
                        ) {
                            speakerLlmExecutionModeOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        homeViewModel.updateSpeakerLlmExecutionMode(value)
                                        speakerLlmModeMenuExpanded = false
                                    },
                                    leadingIcon = {
                                        RadioButton(
                                            selected = value == userSettings.speakerLlmExecutionMode,
                                            onClick = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.speaker_llm_execution_mode_summary),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.home_llm_correction_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.home_llm_correction_summary),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = userSettings.enableHomeLlmCorrection,
                            onCheckedChange = homeViewModel::updateEnableHomeLlmCorrection,
                            enabled = !isDownloading
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.home_english_translation_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.home_english_translation_summary),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = userSettings.enableHomeEnglishTranslation,
                            onCheckedChange = homeViewModel::updateEnableHomeEnglishTranslation,
                            enabled = !isDownloading
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.translate_llm_correction_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.translate_llm_correction_summary),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = userSettings.enableTranslateLlmCorrection,
                            onCheckedChange = homeViewModel::updateEnableTranslateLlmCorrection,
                            enabled = !isDownloading
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.include_remote_candidates_in_variants_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.include_remote_candidates_in_variants_summary),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = userSettings.includeRemoteCandidatesInUtteranceVariants,
                            onCheckedChange = homeViewModel::updateIncludeRemoteCandidatesInUtteranceVariants,
                            enabled = !isDownloading
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.speaker_local_llm_status_label,
                            localLlmStatusText(context, localLlmStatus)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    localLlmProgressText(context, localLlmStatus)?.let { progressText ->
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (localLlmStatus is SpeakerLocalLlmStatus.Downloading) {
                        val progress = localLlmProgress(localLlmStatus)
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { launchLocalLlmDownload() },
                            enabled = !isDownloading &&
                                !isLocalLlmDownloadRunning &&
                                localLlmStatus == SpeakerLocalLlmStatus.Downloadable
                        ) {
                            Text(stringResource(R.string.speaker_local_llm_download))
                        }
                        Button(
                            onClick = { refreshLocalLlmStatus() },
                            enabled = !isDownloading && !isLocalLlmDownloadRunning
                        ) {
                            Text(stringResource(R.string.speaker_local_llm_refresh))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.audio_routing_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(
                            R.string.audio_routing_detected_counts,
                            audioRoutingStatus.inputs.size,
                            audioRoutingStatus.outputs.size
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { homeViewModel.refreshAudioRoutingStatus() },
                        enabled = !isDownloading
                    ) {
                        Text(stringResource(R.string.audio_routing_refresh))
                    }
                }

                AudioRoutingDropdown(
                    label = stringResource(R.string.audio_routing_input_label),
                    expanded = audioInputMenuExpanded,
                    selectedLabel = audioRoutingStatus.selectedInputLabel
                        ?: stringResource(R.string.audio_routing_system_default),
                    devices = audioRoutingStatus.inputs,
                    enabled = !isDownloading,
                    onExpandedChange = { audioInputMenuExpanded = it },
                    onSystemDefaultSelected = {
                        homeViewModel.updatePreferredAudioInputDeviceId(null)
                        audioInputMenuExpanded = false
                    },
                    onDeviceSelected = { device ->
                        homeViewModel.updatePreferredAudioInputDeviceId(device.id)
                        audioInputMenuExpanded = false
                    },
                    selectedDeviceId = userSettings.preferredAudioInputDeviceId
                )

                AudioRoutingDropdown(
                    label = stringResource(R.string.audio_routing_output_label),
                    expanded = audioOutputMenuExpanded,
                    selectedLabel = audioRoutingStatus.selectedOutputLabel
                        ?: stringResource(R.string.audio_routing_system_default),
                    devices = audioRoutingStatus.outputs,
                    enabled = !isDownloading,
                    onExpandedChange = { audioOutputMenuExpanded = it },
                    onSystemDefaultSelected = {
                        homeViewModel.updatePreferredAudioOutputDeviceId(null)
                        audioOutputMenuExpanded = false
                    },
                    onDeviceSelected = { device ->
                        homeViewModel.updatePreferredAudioOutputDeviceId(device.id)
                        audioOutputMenuExpanded = false
                    },
                    selectedDeviceId = userSettings.preferredAudioOutputDeviceId
                )

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.audio_routing_selected_input,
                            audioRoutingStatus.selectedInputLabel
                                ?: context.getString(R.string.audio_routing_system_default)
                        )
                    )
                    Text(
                        text = stringResource(
                            R.string.audio_routing_active_input,
                            audioRoutingStatus.activeInputLabel
                                ?: context.getString(R.string.audio_routing_unavailable)
                        )
                    )
                    Text(
                        text = stringResource(
                            R.string.audio_routing_selected_output,
                            audioRoutingStatus.selectedOutputLabel
                                ?: context.getString(R.string.audio_routing_system_default)
                        )
                    )
                    Text(
                        text = stringResource(
                            R.string.audio_routing_active_output,
                            audioRoutingStatus.activeOutputLabel
                                ?: context.getString(R.string.audio_routing_unavailable)
                        )
                    )
                    Text(
                        text = if (audioRoutingStatus.apiLevelSupportsCommunicationDevice) {
                            stringResource(R.string.audio_routing_comm_device_supported)
                        } else {
                            stringResource(R.string.audio_routing_comm_device_unsupported)
                        }
                    )
                    audioRoutingStatus.lastApplyMessage?.let { message ->
                        Text(
                            text = stringResource(R.string.audio_routing_last_result, message),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.audio_routing_allow_capture))
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = userSettings.allowAppAudioCapture,
                        onCheckedChange = { homeViewModel.updateAllowAppAudioCapture(it) },
                        enabled = !isDownloading
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.audio_routing_prefer_comm_device))
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = userSettings.preferCommunicationDeviceRouting,
                        onCheckedChange = { homeViewModel.updatePreferCommunicationDeviceRouting(it) },
                        enabled = !isDownloading
                    )
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.audio_routing_note_preferred_not_guaranteed),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.audio_routing_note_bluetooth_sco),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.allow_insecure_tls))
                        Text(
                            text = stringResource(R.string.allow_insecure_tls_summary),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = userSettings.allowInsecureTls,
                        onCheckedChange = { homeViewModel.updateAllowInsecureTls(it) },
                        enabled = !isDownloading
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioRoutingDropdown(
    label: String,
    expanded: Boolean,
    selectedLabel: String,
    devices: List<AudioRouteDeviceUi>,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSystemDefaultSelected: () -> Unit,
    onDeviceSelected: (AudioRouteDeviceUi) -> Unit,
    selectedDeviceId: Int?
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) {
                onExpandedChange(!expanded)
            }
        }
    ) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            readOnly = true,
            value = selectedLabel,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.exposedDropdownSize()
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.audio_routing_system_default)) },
                onClick = onSystemDefaultSelected,
                leadingIcon = {
                    RadioButton(
                        selected = selectedDeviceId == null,
                        onClick = null
                    )
                }
            )
            devices.forEach { device ->
                DropdownMenuItem(
                    text = { Text(device.displayLabel) },
                    onClick = { onDeviceSelected(device) },
                    leadingIcon = {
                        RadioButton(
                            selected = selectedDeviceId == device.id,
                            onClick = null
                        )
                    }
                )
            }
        }
    }
}
