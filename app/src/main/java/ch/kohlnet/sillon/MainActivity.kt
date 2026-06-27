package ch.kohlnet.sillon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.theme.SillonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MusicRepository.init(applicationContext)
        PlayerController.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            SillonTheme {
                SillonApp()
            }
        }
    }
}
