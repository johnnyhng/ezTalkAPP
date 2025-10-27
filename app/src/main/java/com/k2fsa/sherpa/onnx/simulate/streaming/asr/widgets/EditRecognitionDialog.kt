package com.k2fsa.sherpa.onnx.simulate.streaming.asr.widgets

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.SimulateStreamingAsr
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.utils.getRemoteCandidates
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.utils.readWavFileToFloatArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

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
    val coroutineScope = rememberCoroutineScope()
    var text by remember { mutableStateOf(currentText) }
    var newRecognitionResult by remember { mutableStateOf<String?>(null) }
    var remoteRecognitionResult by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRecognizing by remember { mutableStateOf(false) }
    var isRemoteRecognizing by remember { mutableStateOf(false) }
    var recognitionError by remember { mutableStateOf<String?>(null) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsSpeaking by remember { mutableStateOf(false) }

    // Use the non-streaming (offline) recognizer for whole-file recognition
    val recognizer: OfflineRecognizer = remember { SimulateStreamingAsr.recognizer }

    // Initialize TTS
    LaunchedEffect(key1 = Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                Log.i(TAG, "TTS initialized successfully for dialog.")
            } else {
                Log.e(TAG, "TTS initialization failed for dialog.")
            }
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    coroutineScope.launch { isTtsSpeaking = true }
                }

                override fun onDone(utteranceId: String?) {
                    coroutineScope.launch {
                        isTtsSpeaking = false
                        onConfirm(text) // Perform confirm action when done
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    coroutineScope.launch { isTtsSpeaking = false }
                }
            })
        }
    }

    // Cleanup TTS
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }


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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Modified Text") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val utteranceId = UUID.randomUUID().toString()
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                        },
                        enabled = !isTtsSpeaking
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Speak and Confirm"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val menuItems =
                    remember(originalText, newRecognitionResult, remoteRecognitionResult, currentText) {
                        (listOfNotNull(
                            originalText,
                            currentText,
                            newRecognitionResult
                        ) + remoteRecognitionResult).distinct()
                    }

                if (isRecognizing || isRemoteRecognizing) {
                    Text("Recognizing...")
                } else {
                    LazyColumn {
                        itemsIndexed(menuItems) { index, item ->
                            Text(
                                text = "${index + 1}: $item",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { text = item }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                }

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
