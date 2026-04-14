package tw.com.johnnyhng.eztalk.asr.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.os.Build

data class AudioRouteDeviceUi(
    val id: Int,
    val productName: String,
    val type: Int,
    val typeLabel: String,
    val isInput: Boolean,
    val isOutput: Boolean,
    val isConnected: Boolean,
    val isCommunicationDeviceCapable: Boolean
) {
    val displayLabel: String
        get() = "$productName ($typeLabel)"
}

data class AudioRoutingStatus(
    val inputs: List<AudioRouteDeviceUi> = emptyList(),
    val outputs: List<AudioRouteDeviceUi> = emptyList(),
    val selectedInputDeviceId: Int? = null,
    val selectedOutputDeviceId: Int? = null,
    val selectedInputLabel: String? = null,
    val selectedOutputLabel: String? = null,
    val activeInputLabel: String? = null,
    val activeOutputLabel: String? = null,
    val lastApplyMessage: String? = null,
    val apiLevelSupportsCommunicationDevice: Boolean = false
)

internal class AudioRoutingRepository(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun describeAvailableInputDevices(): String {
        val inputs = audioManager
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .map { it.toUi(isInput = true, isOutput = false) }
            .sortedBy { it.displayLabel.lowercase() }
        return formatAudioInputDevicesForLog(inputs)
    }

    fun describeAvailableOutputDevices(): String {
        val outputs = audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { it.toUi(isInput = false, isOutput = true) }
            .sortedBy { it.displayLabel.lowercase() }
        return formatAudioOutputDevicesForLog(outputs)
    }

    fun describeAudioManagerState(): String {
        val communicationOutput = resolveCommunicationOutputLabel() ?: "none"
        return "mode=${describeAudioMode(audioManager.mode)} " +
            "speakerphoneOn=${audioManager.isSpeakerphoneOn} " +
            "bluetoothScoOn=${audioManager.isBluetoothScoOn} " +
            "musicActive=${audioManager.isMusicActive} " +
            "communicationDevice=$communicationOutput"
    }

    fun resolveSelectedInputLabel(selectedInputDeviceId: Int?): String? {
        if (selectedInputDeviceId == null) return null
        return audioManager
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.id == selectedInputDeviceId }
            ?.toUi(isInput = true, isOutput = false)
            ?.displayLabel
    }

    fun resolveSelectedInputType(selectedInputDeviceId: Int?): Int? {
        if (selectedInputDeviceId == null) return null
        return audioManager
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.id == selectedInputDeviceId }
            ?.type
    }

    fun resolveSelectedOutputLabel(selectedOutputDeviceId: Int?): String? {
        if (selectedOutputDeviceId == null) return null
        return audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.id == selectedOutputDeviceId }
            ?.toUi(isInput = false, isOutput = true)
            ?.displayLabel
    }

    fun resolveCommunicationOutputLabel(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return audioManager.communicationDevice
            ?.toUi(isInput = false, isOutput = true)
            ?.displayLabel
    }

    fun getStatus(
        selectedInputDeviceId: Int?,
        selectedOutputDeviceId: Int?,
        activeInputLabel: String? = null,
        lastApplyMessage: String? = null
    ): AudioRoutingStatus {
        val inputs = audioManager
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .map { it.toUi(isInput = true, isOutput = false) }
            .sortedBy { it.displayLabel.lowercase() }
        val outputs = audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { it.toUi(isInput = false, isOutput = true) }
            .sortedBy { it.displayLabel.lowercase() }

        val selectedInput = inputs.firstOrNull { it.id == selectedInputDeviceId }
        val selectedOutput = outputs.firstOrNull { it.id == selectedOutputDeviceId }
        val activeCommunicationOutput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.toUi(
                isInput = false,
                isOutput = true
            )?.displayLabel
        } else {
            null
        }

        return AudioRoutingStatus(
            inputs = inputs,
            outputs = outputs,
            selectedInputDeviceId = selectedInputDeviceId,
            selectedOutputDeviceId = selectedOutputDeviceId,
            selectedInputLabel = selectedInput?.displayLabel,
            selectedOutputLabel = selectedOutput?.displayLabel,
            activeInputLabel = activeInputLabel,
            activeOutputLabel = activeCommunicationOutput,
            lastApplyMessage = lastApplyMessage,
            apiLevelSupportsCommunicationDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
    }

    fun applyPreferredInputDevice(
        audioRecord: AudioRecord,
        selectedInputDeviceId: Int?
    ): String {
        val selectedInput = audioManager
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.id == selectedInputDeviceId }
            ?.toUi(isInput = true, isOutput = false)

        val inputApplied = when {
            selectedInputDeviceId == null -> false
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> false
            selectedInput == null -> false
            else -> audioRecord.setPreferredDevice(
                audioManager
                    .getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .firstOrNull { it.id == selectedInputDeviceId }
            )
        }

        return buildPreferredInputRoutingMessage(
            selectedInput = selectedInput,
            inputApplied = inputApplied,
            apiSupportsPreferredDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        )
    }

    fun resolveActiveInputLabel(audioRecord: AudioRecord): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val routedDevice = audioRecord.routedDevice ?: return null
        return routedDevice.toUi(isInput = true, isOutput = false).displayLabel
    }

    fun applyPreferredOutputDevice(
        mediaPlayer: MediaPlayer,
        selectedOutputDeviceId: Int?
    ): String {
        val selectedOutput = audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.id == selectedOutputDeviceId }
            ?.toUi(isInput = false, isOutput = true)

        val outputApplied = when {
            selectedOutputDeviceId == null -> false
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> false
            selectedOutput == null -> false
            else -> mediaPlayer.setPreferredDevice(
                audioManager
                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.id == selectedOutputDeviceId }
            )
        }

        return buildPreferredOutputRoutingMessage(
            selectedOutput = selectedOutput,
            outputApplied = outputApplied,
            apiSupportsPreferredDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        )
    }

    private fun AudioDeviceInfo.toUi(
        isInput: Boolean,
        isOutput: Boolean
    ): AudioRouteDeviceUi {
        return AudioRouteDeviceUi(
            id = id,
            productName = productName?.toString().orEmpty().ifBlank { fallbackTypeLabel(type) },
            type = type,
            typeLabel = fallbackTypeLabel(type),
            isInput = isInput,
            isOutput = isOutput,
            isConnected = true,
            isCommunicationDeviceCapable = type in communicationDeviceTypes
        )
    }

    private fun fallbackTypeLabel(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in mic"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
            else -> "Type $type"
        }
    }

    private companion object {
        val communicationDeviceTypes = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY
        )
    }
}

internal fun formatAudioInputDevicesForLog(inputs: List<AudioRouteDeviceUi>): String {
    if (inputs.isEmpty()) return "none"
    return inputs.joinToString(separator = "; ") { device ->
        "id=${device.id},label=${device.displayLabel},type=${device.type}"
    }
}

internal fun formatAudioOutputDevicesForLog(outputs: List<AudioRouteDeviceUi>): String {
    if (outputs.isEmpty()) return "none"
    return outputs.joinToString(separator = "; ") { device ->
        "id=${device.id},label=${device.displayLabel},type=${device.type}"
    }
}

internal fun describeAudioMode(mode: Int): String {
    return when (mode) {
        AudioManager.MODE_NORMAL -> "MODE_NORMAL"
        AudioManager.MODE_RINGTONE -> "MODE_RINGTONE"
        AudioManager.MODE_IN_CALL -> "MODE_IN_CALL"
        AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
        AudioManager.MODE_CALL_SCREENING -> "MODE_CALL_SCREENING"
        AudioManager.MODE_CALL_REDIRECT -> "MODE_CALL_REDIRECT"
        AudioManager.MODE_COMMUNICATION_REDIRECT -> "MODE_COMMUNICATION_REDIRECT"
        else -> "MODE_$mode"
    }
}

internal fun buildPreferredInputRoutingMessage(
    selectedInput: AudioRouteDeviceUi?,
    inputApplied: Boolean,
    apiSupportsPreferredDevice: Boolean
): String {
    if (selectedInput == null) {
        return if (apiSupportsPreferredDevice) {
            "Using system default microphone route"
        } else {
            "Preferred microphone routing is unavailable on this Android version"
        }
    }

    if (!apiSupportsPreferredDevice) {
        return "Preferred microphone routing is unavailable on this Android version"
    }

    return if (inputApplied) {
        "Preferred microphone requested: ${selectedInput.displayLabel}"
    } else {
        "Preferred microphone request rejected: ${selectedInput.displayLabel}"
    }
}

internal fun buildPreferredOutputRoutingMessage(
    selectedOutput: AudioRouteDeviceUi?,
    outputApplied: Boolean,
    apiSupportsPreferredDevice: Boolean
): String {
    if (selectedOutput == null) {
        return if (apiSupportsPreferredDevice) {
            "Using system default playback route"
        } else {
            "Preferred playback routing is unavailable on this Android version"
        }
    }

    if (!apiSupportsPreferredDevice) {
        return "Preferred playback routing is unavailable on this Android version"
    }

    return if (outputApplied) {
        "Preferred playback route requested: ${selectedOutput.displayLabel}"
    } else {
        "Preferred playback route request rejected: ${selectedOutput.displayLabel}"
    }
}
