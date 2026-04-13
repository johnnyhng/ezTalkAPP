package tw.com.johnnyhng.eztalk.asr.audio

import android.content.Context
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG

internal data class ManagedAudioRecord(
    val audioRecord: AudioRecord?,
    val bufferSize: Int,
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
}
