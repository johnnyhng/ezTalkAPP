package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

import android.media.MediaPlayer
import android.util.Log
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface MediaControllerListener {
    fun onFinishPlayback()
}
object MediaController {
    private var mediaPlayer: MediaPlayer? = null
    private var listener: MediaControllerListener? = null

    private val _currentlyPlaying = MutableStateFlow<String?>(null)
    val currentlyPlaying: StateFlow<String?> = _currentlyPlaying.asStateFlow()

    private fun cleanup() {
        // This function is designed to be safe to call from any state.
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Tried to stop media player in an invalid state.", e)
            }
            // Always try to release
            try {
                player.release()
            } catch (e: Exception) {
                 Log.w(TAG, "Exception during media player release.", e)
            }
        }
        mediaPlayer = null
        if (_currentlyPlaying.value != null) {
            _currentlyPlaying.value = null
        }
    }

    fun setListener(l: MediaControllerListener) {
        listener = l
    }

    fun play(filePath: String) {
        cleanup() // Clean up any previous player first.

        try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    // Playback completed. Just clean up.
                    cleanup()
                }
                setOnErrorListener { _, _, _ ->
                    Log.e(TAG, "MediaPlayer error during playback.")
                    cleanup()
                    true // Error was handled
                }
            }
            mediaPlayer = player
            _currentlyPlaying.value = filePath
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer preparation failed for $filePath", e)
            cleanup()
        } finally {
            listener?.onFinishPlayback()
        }
    }

    fun stop() {
        // This is called by the user clicking a stop button, or on dispose.
        // cleanup() handles all the logic.
        cleanup()
    }
}
