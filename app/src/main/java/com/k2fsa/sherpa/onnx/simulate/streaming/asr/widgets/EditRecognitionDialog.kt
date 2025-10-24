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
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.utils.getRemoteCandidates
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.utils.readWavFileToFloatArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

            launch {
                isRemoteRecognizing = true
                remoteRecognitionResult = getRemoteCandidates(
                    context,
                    wavFilePath,
                    userId,
                    recognitionUrl,
                    originalText,
                    currentText
                )
                isRemoteRecognizing = false
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
