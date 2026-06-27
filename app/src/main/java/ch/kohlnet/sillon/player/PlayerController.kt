package ch.kohlnet.sillon.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import ch.kohlnet.sillon.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lecteur audio (Media3 / ExoPlayer). Singleton, accédé sur le thread principal. Expose le morceau
 * courant et l'état lecture/pause en `StateFlow` pour l'UI.
 *
 * (À venir : `MediaSessionService` pour la lecture en arrière-plan + contrôles système/écran verrouillé,
 * comme `MPNowPlayingInfoCenter` côté iOS.)
 */
object PlayerController {
    private var player: ExoPlayer? = null
    private var queue: List<Track> = emptyList()

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** À appeler une fois au lancement (MainActivity). */
    fun init(context: Context) {
        if (player != null) return
        val p = ExoPlayer.Builder(context.applicationContext).build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _current.value = queue.getOrNull(p.currentMediaItemIndex)
            }
        })
        player = p
    }

    /** Démarre la lecture d'une file de morceaux à partir de `startIndex`. */
    fun play(tracks: List<Track>, startIndex: Int) {
        val p = player ?: return
        queue = tracks
        p.setMediaItems(tracks.map { MediaItem.fromUri(it.streamUrl) }, startIndex, 0L)
        p.prepare()
        p.play()
        _current.value = tracks.getOrNull(startIndex)
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun next() {
        player?.seekToNextMediaItem()
    }

    fun previous() {
        player?.seekToPreviousMediaItem()
    }
}
