package ch.kohlnet.sillon.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import ch.kohlnet.sillon.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lecteur audio (Media3 / ExoPlayer). Singleton, accédé sur le thread principal. Expose le morceau
 * courant, l'état lecture/pause et la position/durée en `StateFlow` pour l'UI.
 *
 * (À venir : `MediaSessionService` pour la lecture en arrière-plan + contrôles système/écran verrouillé,
 * comme `MPNowPlayingInfoCenter` côté iOS.)
 */
object PlayerController {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var player: ExoPlayer? = null
    private var queue: List<Track> = emptyList()

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

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
        // Boucle légère de mise à jour position/durée (thread principal, ExoPlayer y est confiné).
        scope.launch {
            while (true) {
                player?.let {
                    _positionMs.value = it.currentPosition.coerceAtLeast(0)
                    _durationMs.value = it.duration.coerceAtLeast(0)
                }
                delay(500)
            }
        }
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

    fun seekTo(ms: Long) {
        player?.seekTo(ms)
    }
}
