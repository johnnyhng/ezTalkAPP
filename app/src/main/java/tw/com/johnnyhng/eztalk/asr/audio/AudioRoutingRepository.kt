package tw.com.johnnyhng.eztalk.asr.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
    val activeOutputLabel: String? = null,
    val lastApplyMessage: String? = null,
    val apiLevelSupportsCommunicationDevice: Boolean = false
)

internal class AudioRoutingRepository(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun getStatus(
        selectedInputDeviceId: Int?,
        selectedOutputDeviceId: Int?
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
            activeOutputLabel = activeCommunicationOutput,
            apiLevelSupportsCommunicationDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
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
