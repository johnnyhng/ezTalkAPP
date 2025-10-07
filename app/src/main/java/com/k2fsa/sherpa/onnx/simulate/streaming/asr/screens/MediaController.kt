package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaController {
    private var mediaPlayer: MediaPlayer? = null

    private val _currentlyPlaying = MutableStateFlow<String?>(null)
    val currentlyPlaying = _currentlyPlaying.asStateFlow()

    fun play(filePath: String) {
        if (mediaPlayer?.isPlaying == true) {
            stop()
        }
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(filePath)
                prepare()
                start()
                _currentlyPlaying.value = filePath
                setOnCompletionListener {
                    stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer failed for $filePath", e)
                stop()
            }
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _currentlyPlaying.value = null
    }
}
