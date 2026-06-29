package ch.kohlnet.sillon.player

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Suit la route de SORTIE audio active (Bluetooth / casque filaire / haut-parleur) — équivalent de
 * `AudioOutput` iOS (AVAudioSession.currentRoute). Met à jour un `StateFlow` au branchement/débranchement.
 * Le nom d'appareil (ex. nom du casque BT) vient de `AudioDeviceInfo.productName` (pas de permission requise).
 *
 * En Bluetooth, on tente AUSSI d'exposer le CODEC A2DP réel (LDAC/aptX/AAC/SBC) — best-effort : l'API
 * `BluetoothA2dp.getCodecStatus()` est cachée (`@hide`), atteinte par réflexion (HiddenApiBypass) + perm
 * runtime `BLUETOOTH_CONNECT`. Certains OEM/versions la verrouillent en privilégié → on retombe sur `null`
 * (affichage « Bluetooth » seul). Point où Android peut faire mieux qu'iOS (codec A2DP inaccessible côté iOS).
 */
object AudioOutputMonitor {
    enum class Transport { BLUETOOTH, WIRED, SPEAKER, OTHER }

    data class Output(val transport: Transport, val name: String?, val codec: String? = null)

    private val _output = MutableStateFlow(Output(Transport.SPEAKER, null))
    val output: StateFlow<Output> = _output.asStateFlow()

    private var am: AudioManager? = null
    private var appContext: Context? = null
    @Volatile private var a2dp: BluetoothA2dp? = null
    private var receiverRegistered = false

    fun init(context: Context) {
        if (am != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val mgr = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am = mgr
        mgr.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
        }, null)
        setupBluetooth(ctx)
        refresh()
    }

    /** Appelé par l'Activity quand l'utilisateur vient d'accorder BLUETOOTH_CONNECT. */
    fun onBluetoothPermissionGranted() {
        appContext?.let { setupBluetooth(it); refresh() }
    }

    private fun hasBtPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Connecte le proxy A2DP + écoute les changements de codec, si la permission est accordée. */
    private fun setupBluetooth(ctx: Context) {
        if (!hasBtPermission(ctx)) return
        // Exempte l'app du blocage des API non-SDK pour le package bluetooth (getCodecStatus, etc.).
        runCatching { HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/") }
        if (a2dp == null) {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            runCatching {
                adapter?.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        if (profile == BluetoothProfile.A2DP) { a2dp = proxy as? BluetoothA2dp; refresh() }
                    }
                    override fun onServiceDisconnected(profile: Int) {
                        if (profile == BluetoothProfile.A2DP) a2dp = null
                    }
                }, BluetoothProfile.A2DP)
            }
        }
        // Mises à jour LIVE du codec (négociation LDAC, changement de qualité). Action cachée → best-effort.
        if (!receiverRegistered) {
            runCatching {
                ctx.registerReceiver(
                    object : BroadcastReceiver() {
                        override fun onReceive(c: Context?, i: Intent?) = refresh()
                    },
                    IntentFilter("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED"),
                    Context.RECEIVER_EXPORTED,
                )
                receiverRegistered = true
            }
        }
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
                Output(Transport.BLUETOOTH, bt.productName?.toString(), readCodec())
            wired != null -> Output(Transport.WIRED, wired.productName?.toString())
            else -> Output(Transport.SPEAKER, null)
        }
    }

    /**
     * Lit le codec A2DP actif via réflexion sur l'API cachée `getCodecStatus(device)` → `getCodecConfig()`
     * → `getCodecType()`. Tout en runCatching : permission manquante, API privilégiée (Samsung/Android
     * récents) ou aucun appareil connecté → `null` (l'UI affiche alors « Bluetooth » seul).
     */
    private fun readCodec(): String? {
        val proxy = a2dp ?: return null
        val ctx = appContext ?: return null
        if (!hasBtPermission(ctx)) return null
        // NB Android 13+/Samsung : `getCodecStatus` exige en plus une association CDM (CompanionDeviceManager)
        // avec l'appareil, sinon SecurityException → on retombe ici sur null (« Bluetooth » seul).
        return runCatching {
            val device = proxy.connectedDevices.firstOrNull() ?: return null
            val status = BluetoothA2dp::class.java
                .getMethod("getCodecStatus", BluetoothDevice::class.java)
                .invoke(proxy, device) ?: return null
            val config = status.javaClass.getMethod("getCodecConfig").invoke(status) ?: return null
            val type = config.javaClass.getMethod("getCodecType").invoke(config) as? Int ?: return null
            codecName(type)
        }.getOrNull()
    }

    /** Type de codec A2DP (constantes `BluetoothCodecConfig.SOURCE_CODEC_TYPE_*`) → libellé. */
    private fun codecName(type: Int): String? = when (type) {
        0 -> "SBC"
        1 -> "AAC"
        2 -> "aptX"
        3 -> "aptX HD"
        4 -> "LDAC"
        5 -> "LC3"
        6 -> "Opus"
        else -> null
    }
}
