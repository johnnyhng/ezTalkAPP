package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.SimulateStreamingAsr
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val sampleRateInHz = 16000

@Composable
internal fun EditRecognitionDialog(
    originalText: String,
    currentText: String,
    wavFilePath: String, // We need the path to the audio file
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentText) }
    var newRecognitionResult by remember { mutableStateOf<String?>(null) }
    var isRecognizing by remember { mutableStateOf(false) }
    var recognitionError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Use the non-streaming (offline) recognizer for whole-file recognition
    val recognizer: OfflineRecognizer = remember { SimulateStreamingAsr.recognizer }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Recognition") },
        text = {
            Column {
                Text("Original:", fontWeight = FontWeight.Bold)
                Text(originalText)
                Spacer(modifier = Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Modified Text") }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // "Recognize again" functionality
                if (wavFilePath.isNotEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    isRecognizing = true
                                    newRecognitionResult = null
                                    recognitionError = null
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
                                            launch(Dispatchers.Main) {
                                                newRecognitionResult = result
                                            }
                                            stream.release()
                                        } else {
                                            launch(Dispatchers.Main) {
                                                recognitionError =
                                                    "Error: Could not read audio file."
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Re-recognition failed", e)
                                        launch(Dispatchers.Main) {
                                            recognitionError = "Error: Recognition failed."
                                        }
                                    } finally {
                                        launch(Dispatchers.Main) {
                                            isRecognizing = false
                                        }
                                    }
                                }
                            },
                            enabled = !isRecognizing
                        ) {
                            Text(text = "Recognize again")
                        }

                        if (isRecognizing) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                        }

                        recognitionError?.let { error ->
                            Text(error, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
                        }

                        newRecognitionResult?.let { result ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("New Result:", fontWeight = FontWeight.Bold)
                            Text(result)
                            TextButton(onClick = {
                                text = result
                                newRecognitionResult = null // Clear after applying
                            }) {
                                Text("Replace with new result")
                            }
                        }
                    }
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

/**
 * Reads a WAV file and returns its audio data as a FloatArray.
 * Note: This makes a simplifying assumption that the WAV file is 16-bit PCM.
 *
 * @param path The absolute path to the WAV file.
 * @return A FloatArray containing the audio samples normalized to [-1, 1], or null on error.
 */
private fun readWavFileToFloatArray(path: String): FloatArray? {
    try {
        val file = File(path)
        val fileInputStream = FileInputStream(file)
        val byteBuffer = fileInputStream.readBytes()
        fileInputStream.close()

        // The first 44 bytes of a standard WAV file are the header. We skip it.
        val headerSize = 44
        if (byteBuffer.size <= headerSize) {
            Log.e(TAG, "WAV file is too small to contain audio data: ${file.name}")
            return null
        }

        // We assume the audio data is 16-bit PCM, little-endian.
        val shortBuffer = ByteBuffer.wrap(byteBuffer, headerSize, byteBuffer.size - headerSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val numSamples = shortBuffer.remaining()
        val floatArray = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            // Convert 16-bit short to float in the range [-1.0, 1.0]
            floatArray[i] = shortBuffer.get(i) / 32768.0f
        }
        return floatArray
    } catch (e: Exception) {
        Log.e(TAG, "Error reading WAV file: $path", e)
        return null
    }
}
