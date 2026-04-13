package tw.com.johnnyhng.eztalk.asr.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioRecord
import android.media.MediaPlayer
import android.os.Build
import android.media.MediaRecorder
import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings

internal data class ManagedAudioRecord(
    val audioRecord: AudioRecord?,
    val bufferSize: Int,
    val routingMessage: String? = null
)

internal data class ManagedMediaPlayer(
    val mediaPlayer: MediaPlayer?,
    val routingMessage: String? = null
)

internal class AudioIOManager(
    context: Context,
    private val audioRoutingRepository: AudioRoutingRepository = AudioRoutingRepository(context)
) {
    fun createMicAudioRecord(
        sampleRateInHz: Int,
        channelConfig: Int,
        audioFormat: Int,
        preferredInputDeviceId: Int?,
        audioSource: Int = MediaRecorder.AudioSource.MIC
    ): ManagedAudioRecord {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        if (minBufferSize <= 0) {
            val message = "AudioRecord min buffer unavailable: size=$minBufferSize"
            Log.e(TAG, message)
            return ManagedAudioRecord(
                audioRecord = null,
                bufferSize = minBufferSize,
                routingMessage = message
            )
        }

        val audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            minBufferSize * 2
        )

        val routingMessage = audioRoutingRepository.applyPreferredInputDevice(
            audioRecord = audioRecord,
            selectedInputDeviceId = preferredInputDeviceId
        )

        return ManagedAudioRecord(
            audioRecord = audioRecord,
            bufferSize = minBufferSize,
            routingMessage = routingMessage
        )
    }

    fun resolveActiveInputLabel(audioRecord: AudioRecord?): String? {
        if (audioRecord == null) return null
        return audioRoutingRepository.resolveActiveInputLabel(audioRecord)
    }

    fun createPlaybackMediaPlayer(
        filePath: String,
        userSettings: UserSettings
    ): ManagedMediaPlayer {
        return try {
            val player = MediaPlayer().apply {
                setAudioAttributes(buildPlaybackAttributes(userSettings.allowAppAudioCapture))
                setDataSource(filePath)
            }
            val routingMessage = audioRoutingRepository.applyPreferredOutputDevice(
                mediaPlayer = player,
                selectedOutputDeviceId = userSettings.preferredAudioOutputDeviceId
            )
            ManagedMediaPlayer(
                mediaPlayer = player,
                routingMessage = routingMessage
            )
        } catch (error: Exception) {
            Log.e(TAG, "Failed to create playback media player for $filePath", error)
            ManagedMediaPlayer(
                mediaPlayer = null,
                routingMessage = "Playback initialization failed"
            )
        }
    }

    private fun buildPlaybackAttributes(allowAppAudioCapture: Boolean): AudioAttributes {
        val builder = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setAllowedCapturePolicy(
                if (allowAppAudioCapture) {
                    AudioAttributes.ALLOW_CAPTURE_BY_ALL
                } else {
                    AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM
                }
            )
        }

        return builder.build()
    }
}
