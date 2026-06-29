package ch.kohlnet.sillon

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import ch.kohlnet.sillon.data.AppSettings
import ch.kohlnet.sillon.data.AppearanceMode
import ch.kohlnet.sillon.data.EqualizerState
import ch.kohlnet.sillon.data.LanguageManager
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.data.PlayHistory
import ch.kohlnet.sillon.data.Playlists
import ch.kohlnet.sillon.data.SpectrumPrefs
import ch.kohlnet.sillon.player.AudioOutputMonitor
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.theme.SillonTheme

class MainActivity : ComponentActivity() {

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // BLUETOOTH_CONNECT : permet de lire le codec A2DP réel (LDAC/aptX…) du périphérique connecté.
    private val requestBtConnect =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) AudioOutputMonitor.onBluetoothPermissionGranted()
        }

    // Adresses BT déjà proposées à l'association cette session (évite de redemander en boucle).
    private val promptedBtAddresses = mutableSetOf<String>()

    // Dialogue système d'association CompanionDeviceManager : à l'acceptation → relire le codec.
    private val associateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) AudioOutputMonitor.onBluetoothPermissionGranted()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MusicRepository.init(applicationContext)
        PlayerController.init(applicationContext)
        AppSettings.init(applicationContext)
        LanguageManager.init(applicationContext)
        AudioOutputMonitor.init(applicationContext)
        EqualizerState.init(applicationContext)
        SpectrumPrefs.init(applicationContext)
        PlayHistory.init(applicationContext)
        Playlists.init(applicationContext)
        requestNotificationPermission()
        requestBluetoothPermission()
        // Quand un casque BT est là mais le codec illisible (pas d'association CDM) → proposer l'association.
        AudioOutputMonitor.onCodecBlocked = { device -> runOnUiThread { maybeAssociateCompanion(device) } }
        enableEdgeToEdge()
        setContent {
            val appearance by AppSettings.appearance.collectAsState()
            val dark = when (appearance) {
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
            }
            SillonTheme(darkTheme = dark) {
                SillonApp()
            }
        }
    }

    /** Notification média (lecture en arrière-plan) : permission runtime requise sur Android 13+. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Bluetooth : autorise la lecture du codec A2DP actif (best-effort). Sans elle → « Bluetooth » seul. */
    private fun requestBluetoothPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestBtConnect.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    /**
     * Propose une association CompanionDeviceManager avec [device] (dialogue système ponctuel), nécessaire
     * sous Android 13+ pour lire le codec A2DP. Une seule fois par appareil/session ; si déjà associé, on
     * relit simplement le codec.
     */
    private fun maybeAssociateCompanion(device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val address = runCatching { device.address }.getOrNull() ?: return
        if (address in promptedBtAddresses) return
        val cdm = getSystemService(CompanionDeviceManager::class.java) ?: return
        val alreadyAssociated = runCatching {
            cdm.myAssociations.any { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }
        }.getOrDefault(false)
        if (alreadyAssociated) { AudioOutputMonitor.onBluetoothPermissionGranted(); return }
        promptedBtAddresses += address
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setSingleDevice(true)
            .build()
        runCatching {
            cdm.associate(request, mainExecutor, object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    runCatching { associateLauncher.launch(IntentSenderRequest.Builder(intentSender).build()) }
                }
                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    AudioOutputMonitor.onBluetoothPermissionGranted()
                }
                override fun onFailure(error: CharSequence?) {}
            })
        }
    }

    override fun onDestroy() {
        AudioOutputMonitor.onCodecBlocked = null // éviter de retenir l'Activity
        super.onDestroy()
    }
}
