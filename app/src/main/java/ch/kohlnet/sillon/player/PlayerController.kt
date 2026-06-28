package ch.kohlnet.sillon.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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
 * Pilote la lecture via un `MediaController` connecté à [PlaybackService] (Media3). Le lecteur vit
 * dans le service → lecture en ARRIÈRE-PLAN + contrôles système (notification, écran verrouillé),
 * façon iOS. Expose le morceau courant, l'état lecture/pause et la position/durée en `StateFlow`.
 * Singleton, accédé sur le thread principal.
 */
object PlayerController {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controller: MediaController? = null

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    /** Volume applicatif du lecteur (0..1), façon iOS (n'agit pas sur le volume système). */
    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    /** `Player.REPEAT_MODE_OFF` / `_ALL` / `_ONE`. */
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _current.value = _queue.value.getOrNull(controller?.currentMediaItemIndex ?: -1)
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _shuffle.value = enabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onVolumeChanged(volume: Float) {
            _volume.value = volume
        }
    }

    /** À appeler une fois au lancement (MainActivity) : connecte le MediaController au service. */
    fun init(context: Context) {
        if (controller != null) return
        val ctx = context.applicationContext
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            _isPlaying.value = c.isPlaying
            _volume.value = c.volume
            _current.value = _queue.value.getOrNull(c.currentMediaItemIndex)
        }, ContextCompat.getMainExecutor(ctx))

        // Boucle légère de mise à jour position/durée (thread principal).
        scope.launch {
            while (true) {
                controller?.let {
                    _positionMs.value = it.currentPosition.coerceAtLeast(0)
                    _durationMs.value = it.duration.coerceAtLeast(0)
                }
                delay(500)
            }
        }
    }

    /** Démarre la lecture d'une file de morceaux à partir de `startIndex`. */
    fun play(tracks: List<Track>, startIndex: Int) {
        val c = controller ?: return
        _queue.value = tracks
        val items = tracks.map { t ->
            MediaItem.Builder()
                .setUri(t.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .setArtworkUri(t.coverUrl?.let(Uri::parse))
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.play()
        _current.value = tracks.getOrNull(startIndex)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    /** Avance de 10 s (façon iOS), borné à la durée. */
    fun skipForward(ms: Long = 10_000) {
        controller?.let { it.seekTo((it.currentPosition + ms).coerceAtMost(it.duration.coerceAtLeast(0))) }
    }

    /** Recule de 10 s (façon iOS), borné à 0. */
    fun skipBackward(ms: Long = 10_000) {
        controller?.let { it.seekTo((it.currentPosition - ms).coerceAtLeast(0)) }
    }

    /** Règle le volume applicatif (0..1). */
    fun setVolume(v: Float) {
        val vol = v.coerceIn(0f, 1f)
        controller?.volume = vol
        _volume.value = vol
    }

    /** Saute au morceau d'index `index` dans la file. */
    fun playIndex(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0L)
        c.play()
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** Cycle Off → All → One → Off. */
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
}
