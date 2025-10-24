package com.k2fsa.sherpa.onnx.simulate.streaming.asr.widgets

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.SimulateStreamingAsr
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.utils.postForRecognition
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.utils.readJsonl
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.utils.readWavFileToFloatArray
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.utils.saveJsonl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val sampleRateInHz = 16000

@Composable
internal fun EditRecognitionDialog(
    originalText: String,
    currentText: String,
    wavFilePath: String, // We need the path to the audio file
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    userId: String,
    recognitionUrl: String,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(currentText) }
    var newRecognitionResult by remember { mutableStateOf<String?>(null) }
    var remoteRecognitionResult by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRecognizing by remember { mutableStateOf(false) }
    var isRemoteRecognizing by remember { mutableStateOf(false) }
    var recognitionError by remember { mutableStateOf<String?>(null) }

    // Use the non-streaming (offline) recognizer for whole-file recognition
    val recognizer: OfflineRecognizer = remember { SimulateStreamingAsr.recognizer }

    // Automatically recognize when the dialog appears
    LaunchedEffect(wavFilePath) {
        if (wavFilePath.isNotEmpty()) {
            isRecognizing = true
            newRecognitionResult = null
            recognitionError = null
            launch(Dispatchers.IO) {
                try {
                    val audioData = readWavFileToFloatArray(wavFilePath)
                    if (audioData != null) {
                        val stream = recognizer.createStream()
                        stream.acceptWaveform(
                            audioData,
                            sampleRateInHz
                        )
                        recognizer.decode(stream)
                        val result = recognizer.getResult(stream).text
                        withContext(Dispatchers.Main) {
                            newRecognitionResult = result
                        }
                        stream.release()
                    } else {
                        withContext(Dispatchers.Main) {
                            recognitionError =
                                "Error: Could not read audio file."
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Re-recognition failed", e)
                    withContext(Dispatchers.Main) {
                        recognitionError = "Error: Recognition failed."
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isRecognizing = false
                    }
                }
            }

            // Check for existing remote candidates in the jsonl file
            val jsonlFile = File(wavFilePath).resolveSibling(
                File(wavFilePath).nameWithoutExtension + ".jsonl"
            )

            launch(Dispatchers.IO) {
                val jsonlData = readJsonl(jsonlFile.absolutePath)
                val existingCandidates = jsonlData?.optJSONArray("remote_candidates")

                if (existingCandidates != null && existingCandidates.length() > 0) {
                    Log.d(TAG, "Found existing remote candidates in jsonl file")
                    val sentences = mutableListOf<String>()
                    for (i in 0 until existingCandidates.length()) {
                        sentences.add(existingCandidates.getString(i))
                    }
                    withContext(Dispatchers.Main) {
                        remoteRecognitionResult = sentences
                    }
                } else if (recognitionUrl.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        isRemoteRecognizing = true
                    }
                    val response = postForRecognition(recognitionUrl, wavFilePath, userId)
                    if (response != null) {
                        try {
                            val candidates = response.getJSONArray("sentence_candidates")
                            val sentences = mutableListOf<String>()
                            for (i in 0 until candidates.length()) {
                                sentences.add(candidates.getString(i))
                            }
                            withContext(Dispatchers.Main) {
                                remoteRecognitionResult = sentences
                            }

                            // Save to jsonl
                            val file = File(wavFilePath)
                            val filename = file.nameWithoutExtension
                            val original =
                                jsonlData?.optString("original", originalText) ?: originalText
                            val modified =
                                jsonlData?.optString("modified", currentText) ?: currentText
                            val checked = jsonlData?.optBoolean("checked", false) ?: false

                            saveJsonl(
                                context = context,
                                userId = userId,
                                filename = filename,
                                originalText = original,
                                modifiedText = modified,
                                checked = checked,
                                remoteCandidates = sentences
                            )

                        } catch (e: Exception) {
                            Log.e(TAG, "Could not parse remote recognition result", e)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        isRemoteRecognizing = false
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Recognition") },
        text = {
            Column {
                Text("Original:", fontWeight = FontWeight.Bold)
                Text(originalText)
                Spacer(modifier = Modifier.padding(vertical = 8.dp))

                val menuItems =
                    remember(newRecognitionResult, remoteRecognitionResult, currentText) {
                        (listOfNotNull(
                            newRecognitionResult,
                            currentText
                        ) + remoteRecognitionResult).distinct()
                    }

                EditableDropdown(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Modified Text") },
                    menuItems = menuItems,
                    isRecognizing = isRecognizing || isRemoteRecognizing
                )

                recognitionError?.let {
                    Text(it, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}
