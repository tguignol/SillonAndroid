package ch.kohlnet.sillon.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Suit la route de SORTIE audio active (Bluetooth / casque filaire / haut-parleur) — équivalent de
 * `AudioOutput` iOS (AVAudioSession.currentRoute). Met à jour un `StateFlow` au branchement/débranchement.
 * Le nom d'appareil (ex. nom du casque BT) vient de `AudioDeviceInfo.productName` (pas de permission requise).
 */
object AudioOutputMonitor {
    enum class Transport { BLUETOOTH, WIRED, SPEAKER, OTHER }

    data class Output(val transport: Transport, val name: String?)

    private val _output = MutableStateFlow(Output(Transport.SPEAKER, null))
    val output: StateFlow<Output> = _output.asStateFlow()

    private var am: AudioManager? = null

    fun init(context: Context) {
        if (am != null) return
        val mgr = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am = mgr
        mgr.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
        }, null)
        refresh()
    }

    @Suppress("DEPRECATION")
    private fun refresh() {
        val mgr = am ?: return
        val outs = mgr.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        fun kind(t: Int) = when (t) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> Transport.BLUETOOTH
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_LINE_ANALOG -> Transport.WIRED
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Transport.SPEAKER
            else -> Transport.OTHER
        }
        val bt = outs.firstOrNull { kind(it.type) == Transport.BLUETOOTH }
        val wired = outs.firstOrNull { kind(it.type) == Transport.WIRED }
        _output.value = when {
            bt != null && (mgr.isBluetoothA2dpOn || mgr.isBluetoothScoOn) ->
                Output(Transport.BLUETOOTH, bt.productName?.toString())
            wired != null -> Output(Transport.WIRED, wired.productName?.toString())
            else -> Output(Transport.SPEAKER, null)
        }
    }
}
