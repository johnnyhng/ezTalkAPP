package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.io.File

/**
 * Data class to hold a WAV file and the content of its corresponding JSONL file.
 */
data class WavFileEntry(val wavFile: File, val jsonlContent: List<String>)

@Composable
fun FileManagerScreen() {
    val context = LocalContext.current
    var wavFileEntries by remember { mutableStateOf<List<WavFileEntry>>(emptyList()) }
    var currentlyPlaying by remember { mutableStateOf<File?>(null) }
    val mediaPlayer by remember { mutableStateOf(MediaPlayer()) }

    fun listWavFiles() {
        val wavsDir = File(context.filesDir, "wavs")
        if (wavsDir.exists()) {
            wavFileEntries = wavsDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".wav") }
                .sortedByDescending { it.lastModified() }
                .map { wavFile ->
                    val jsonlFile = File(wavFile.parent, "${wavFile.nameWithoutExtension}.jsonl")
                    val content = if (jsonlFile.exists()) {
                        jsonlFile.readLines().mapNotNull { line ->
                            // Parse JSON and format it for display
                            try {
                                val json = JSONObject(line)
                                val original = json.keys().next()
                                val modified = json.getString(original)
                                "Original: $original\nModified: $modified"
                            } catch (e: Exception) {
                                null // Ignore malformed lines
                            }
                        }
                    } else {
                        emptyList()
                    }
                    WavFileEntry(wavFile, content)
                }.toList()
        } else {
            wavFileEntries = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        listWavFiles()
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            val wavsDir = File(context.filesDir, "wavs")
            if (wavsDir.exists()) {
                wavsDir.deleteRecursively()
            }
            listWavFiles()
        }, enabled = currentlyPlaying == null) {
            Text("Delete all wavs")
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
                        val isPlaying = currentlyPlaying == entry.wavFile
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                entry.wavFile.path.substringAfter(context.filesDir.path + "/"),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        mediaPlayer.stop()
                                        mediaPlayer.reset()
                                        currentlyPlaying = null
                                    } else {
                                        currentlyPlaying = entry.wavFile
                                        mediaPlayer.apply {
                                            reset()
                                            setDataSource(entry.wavFile.absolutePath)
                                            prepare()
                                            start()
                                            setOnCompletionListener {
                                                reset()
                                                currentlyPlaying = null
                                            }
                                        }
                                    }
                                },
                                enabled = currentlyPlaying == null || isPlaying
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Stop" else "Playback"
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

                                    // Also delete parent directories if they become empty
                                    var parent = entry.wavFile.parentFile
                                    while (parent != null && parent.name != "wavs" && parent.listFiles()
                                            ?.isEmpty() == true
                                    ) {
                                        parent.delete()
                                        parent = parent.parentFile
                                    }
                                    listWavFiles()
                                },
                                enabled = currentlyPlaying == null
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete"
                                )
                            }
                        }

                        // Display JSONL content if available
                        if (entry.jsonlContent.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            entry.jsonlContent.forEach { contentLine ->
                                Text(
                                    text = contentLine,
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
    }
}
