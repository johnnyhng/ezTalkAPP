package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FileManagerScreen() {
    val context = LocalContext.current
    var wavFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var currentlyPlaying by remember { mutableStateOf<File?>(null) }
    val mediaPlayer by remember { mutableStateOf(MediaPlayer()) }

    fun listWavFiles() {
        val wavsDir = File(context.filesDir, "wavs")
        if (wavsDir.exists()) {
            // Recursively find all files ending with .wav
            wavFiles = wavsDir.walkTopDown().filter { it.isFile && it.name.endsWith(".wav") }
                .sortedByDescending { it.lastModified() }
                .toList()
        } else {
            wavFiles = emptyList()
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
            items(wavFiles) { file ->
                val isPlaying = currentlyPlaying == file
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        file.path.substringAfter(context.filesDir.path + "/"),
                        modifier = Modifier.weight(1f)
                    ) // Show relative path
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                mediaPlayer.stop()
                                mediaPlayer.reset()
                                currentlyPlaying = null
                            } else {
                                currentlyPlaying = file
                                mediaPlayer.apply {
                                    reset()
                                    setDataSource(file.absolutePath)
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
                            file.delete()
                            // Also delete parent directories if they become empty
                            var parent = file.parentFile
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
            }
        }
    }
}
