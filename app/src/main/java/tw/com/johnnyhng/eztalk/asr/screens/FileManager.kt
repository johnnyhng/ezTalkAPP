package tw.com.johnnyhng.eztalk.asr.screens

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tw.com.johnnyhng.eztalk.asr.R
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.utils.MediaController
import tw.com.johnnyhng.eztalk.asr.utils.feedbackToBackend
import tw.com.johnnyhng.eztalk.asr.utils.saveJsonl
import tw.com.johnnyhng.eztalk.asr.widgets.EditRecognitionDialog
import java.io.File

/**
 * Data class to hold a WAV file and the content of its corresponding JSONL file.
 */
data class WavFileEntry(
    val wavFile: File,
    val originalText: String,
    val modifiedText: String,
    val checked: Boolean,
    val mutable: Boolean,
)

@SuppressLint("StringFormatInvalid")
@Composable
fun FileManagerScreen(homeViewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val userSettings by homeViewModel.userSettings.collectAsState()
    var wavFileEntries by remember { mutableStateOf<List<WavFileEntry>>(emptyList()) }
    val currentlyPlaying by MediaController.currentlyPlaying.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Edit dialog state
    var showEditDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<WavFileEntry?>(null) }

    // Feedback state
    var isFeedbackInProgress by remember { mutableStateOf(false) }
    var feedbackProgress by remember { mutableStateOf(0f) }
    var feedbackProgressText by remember { mutableStateOf("") }

    fun listWavFiles() {
        val wavsDir = File(context.filesDir, "wavs/${userSettings.userId}")
        if (wavsDir.exists()) {
            wavFileEntries = wavsDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".wav") }
                .sortedByDescending { it.lastModified() }
                .mapNotNull { wavFile ->
                    val jsonlFile = File(wavFile.parent, "${wavFile.nameWithoutExtension}.jsonl")
                    if (jsonlFile.exists()) {
                        try {
                            val line = jsonlFile.readText()
                            val json = JSONObject(line)
                            val original = json.getString("original")
                            val modified = json.getString("modified")
                            val checked = json.getBoolean("checked")
                            val mutable = json.optBoolean("mutable", true)

                            WavFileEntry(
                                wavFile = wavFile,
                                originalText = original,
                                modifiedText = modified,
                                checked = checked,
                                mutable = mutable
                            )
                        } catch (e: Exception) {
                            null // Ignore malformed lines
                        }
                    } else {
                        null
                    }
                }.toList()
        } else {
            wavFileEntries = emptyList()
        }
    }

    fun feedback(selectedFiles: List<WavFileEntry>) {
        coroutineScope.launch {
            isFeedbackInProgress = true
            feedbackProgress = 0f
            feedbackProgressText = "0/${selectedFiles.size}"
            var successCount = 0
            withContext(Dispatchers.IO) {
                selectedFiles.forEachIndexed { index, entry ->
                    val success =
                        feedbackToBackend(
                            userSettings.backendUrl,
                            entry.wavFile.absolutePath,
                            userSettings.userId
                        )
                    if (success) {
                        successCount++
                        // Delete the wav and jsonl files
                        val jsonlFile =
                            File(entry.wavFile.parent, "${entry.wavFile.nameWithoutExtension}.jsonl")
                        entry.wavFile.delete()
                        jsonlFile.delete()
                    }
                    // Update progress on the main thread
                    withContext(Dispatchers.Main) {
                        feedbackProgress = (index + 1).toFloat() / selectedFiles.size
                        feedbackProgressText = "${index + 1}/${selectedFiles.size}"
                    }
                }
            }

            Toast.makeText(
                context,
                context.getString(R.string.feedback_submitted, successCount, selectedFiles.size),
                Toast.LENGTH_SHORT
            ).show()
            isFeedbackInProgress = false
            listWavFiles() // Refresh the list
        }
    }



    LaunchedEffect(userSettings.userId) {
        listWavFiles()
    }

    DisposableEffect(Unit) {
        onDispose {
            MediaController.stop()
        }
    }

    if (showEditDialog && editingEntry != null) {
        EditRecognitionDialog(
            originalText = editingEntry!!.originalText,
            currentText = editingEntry!!.modifiedText,
            wavFilePath = editingEntry!!.wavFile.absolutePath,
            onDismiss = { showEditDialog = false },
            onConfirm = { newText ->
                coroutineScope.launch {
                    saveJsonl(
                        context = context,
                        userId = userSettings.userId,
                        filename = editingEntry!!.wavFile.nameWithoutExtension,
                        originalText = editingEntry!!.originalText,
                        modifiedText = newText,
                        checked = editingEntry!!.checked,
                        mutable = editingEntry!!.mutable
                    )
                    listWavFiles() // Refresh the list
                }
                showEditDialog = false
            },
            userId = userSettings.userId,
            recognitionUrl = userSettings.recognitionUrl
        )
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row {
            Button(onClick = {
                val selectedFiles = wavFileEntries.filter { it.checked }
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        selectedFiles.forEach { entry ->
                            val jsonlFile =
                                File(
                                    entry.wavFile.parent,
                                    "${entry.wavFile.nameWithoutExtension}.jsonl"
                                )
                            entry.wavFile.delete()
                            if (jsonlFile.exists()) {
                                jsonlFile.delete()
                            }
                        }
                    }
                    listWavFiles()
                    Toast.makeText(
                        context,
                        context.getString(R.string.files_deleted, selectedFiles.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }, enabled = wavFileEntries.any { it.checked } && !isFeedbackInProgress) {
                Text(stringResource(R.string.delete_selected))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = {
                feedback(wavFileEntries.filter { it.checked })
            }, enabled = wavFileEntries.any { it.checked } && !isFeedbackInProgress) {
                Text(stringResource(R.string.feedback))
            }
        }

        if (isFeedbackInProgress) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(progress = feedbackProgress, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            Text(feedbackProgressText, style = MaterialTheme.typography.bodySmall)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val mutableEntries = wavFileEntries.filter { it.mutable }
            val allMutableSelected = mutableEntries.isNotEmpty() && mutableEntries.all { it.checked }
            Checkbox(
                checked = allMutableSelected,
                onCheckedChange = {
                    val newState = !allMutableSelected
                    coroutineScope.launch {
                        wavFileEntries.forEach { entry ->
                            if (entry.mutable) {
                                saveJsonl(
                                    context = context,
                                    userId = userSettings.userId,
                                    filename = entry.wavFile.nameWithoutExtension,
                                    originalText = entry.originalText,
                                    modifiedText = entry.modifiedText,
                                    checked = newState,
                                    mutable = entry.mutable
                                )
                            }
                        }
                        listWavFiles()
                    }
                },
                enabled = !isFeedbackInProgress && mutableEntries.isNotEmpty()
            )
            Text(stringResource(R.string.select_all))
        }


        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(wavFileEntries) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = entry.checked,
                                onCheckedChange = {
                                    coroutineScope.launch {
                                        saveJsonl(
                                            context = context,
                                            userId = userSettings.userId,
                                            filename = entry.wavFile.nameWithoutExtension,
                                            originalText = entry.originalText,
                                            modifiedText = entry.modifiedText,
                                            checked = it,
                                            mutable = entry.mutable
                                        )
                                        listWavFiles()
                                    }
                                },
                                enabled = !isFeedbackInProgress && entry.mutable
                            )
                            Text(
                                entry.wavFile.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    feedback(listOf(entry))
                                },
                                enabled = !isFeedbackInProgress
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Upload,
                                    contentDescription = stringResource(R.string.upload)
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingEntry = entry
                                    showEditDialog = true
                                },
                                enabled = currentlyPlaying == null && !isFeedbackInProgress && entry.mutable
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.edit)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (currentlyPlaying == entry.wavFile.absolutePath) {
                                        MediaController.stop()
                                    } else {
                                        MediaController.play(entry.wavFile.absolutePath)
                                    }
                                },
                                enabled = !isFeedbackInProgress && (currentlyPlaying == null || currentlyPlaying == entry.wavFile.absolutePath)
                            ) {
                                Icon(
                                    imageVector = if (currentlyPlaying == entry.wavFile.absolutePath) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = if (currentlyPlaying == entry.wavFile.absolutePath) stringResource(R.string.stop) else stringResource(R.string.playback)
                                )
                            }
                            IconButton(
                                onClick = {
                                    // Delete both the .wav and the .jsonl file
                                    val jsonlFile = File(
                                        entry.wavFile.parent,
                                        "${entry.wavFile.nameWithoutExtension}.jsonl"
                                    )
                                    entry.wavFile.delete()
                                    if (jsonlFile.exists()) {
                                        jsonlFile.delete()
                                    }
                                    listWavFiles()
                                },
                                enabled = currentlyPlaying == null && !isFeedbackInProgress
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }
                        }

                        // Display JSONL content
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.original_text, entry.originalText) + "\n" +
                                    stringResource(R.string.modified_text, entry.modifiedText),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
