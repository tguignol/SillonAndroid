package ch.kohlnet.sillon.player

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import ch.kohlnet.sillon.data.PlayHistory
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

    /** Volume MÉDIA du système (0..1) — à fond = vraiment fort. Réglé par la barre du lecteur. */
    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()
    private var audioManager: AudioManager? = null

    /** `Player.REPEAT_MODE_OFF` / `_ALL` / `_ONE`. */
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val t = _queue.value.getOrNull(controller?.currentMediaItemIndex ?: -1)
            _current.value = t
            if (t != null) PlayHistory.record(t) // historique d'écoute (accueil : plus écoutés / récemment écoutés)
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _shuffle.value = enabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }
    }

    /** À appeler une fois au lancement (MainActivity) : connecte le MediaController au service. */
    fun init(context: Context) {
        if (controller != null) return
        val ctx = context.applicationContext
        audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        refreshVolume()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            c.volume = 1f   // volume applicatif à fond : c'est le volume SYSTÈME qu'on règle
            _isPlaying.value = c.isPlaying
            _current.value = _queue.value.getOrNull(c.currentMediaItemIndex)
        }, ContextCompat.getMainExecutor(ctx))

        // Boucle légère de mise à jour position/durée (thread principal).
        scope.launch {
            while (true) {
                controller?.let {
                    _positionMs.value = it.currentPosition.coerceAtLeast(0)
                    _durationMs.value = it.duration.coerceAtLeast(0)
                }
                refreshVolume() // synchronise la barre de volume du lecteur avec les touches physiques
                delay(500)
            }
        }
    }

    private fun mediaItem(t: Track): MediaItem =
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

    /** Démarre la lecture d'une file de morceaux à partir de `startIndex`. */
    fun play(tracks: List<Track>, startIndex: Int) {
        val c = controller ?: return
        _queue.value = tracks
        c.setMediaItems(tracks.map(::mediaItem), startIndex, 0L)
        c.prepare()
        c.play()
        _current.value = tracks.getOrNull(startIndex)
    }

    /** Ajoute un titre À LA FIN de la file d'attente (démarre la lecture si la file était vide). */
    fun addToQueue(track: Track) {
        val c = controller ?: return
        if (_queue.value.isEmpty()) { play(listOf(track), 0); return }
        c.addMediaItem(mediaItem(track))
        _queue.value = _queue.value + track
    }

    /** Insère un titre JUSTE APRÈS le morceau courant (« Lire ensuite »). */
    fun playNext(track: Track) {
        val c = controller ?: return
        if (_queue.value.isEmpty()) { play(listOf(track), 0); return }
        val at = (c.currentMediaItemIndex + 1).coerceIn(0, _queue.value.size)
        c.addMediaItem(at, mediaItem(track))
        _queue.value = _queue.value.toMutableList().also { it.add(at, track) }
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

    /** Dernier pas système réglé PAR NOUS (pour repérer un changement EXTERNE = touches physiques). */
    private var lastSystemStep = -1

    /**
     * Règle le volume « perçu » 0..1. Le volume média système n'a que des pas ENTIERS ; pour offrir
     * des DEMI-PAS (plus fin que les touches physiques), on combine le pas système (grossier) avec le
     * volume du lecteur (fin, continu) : perçu ≈ (pas/max) × volumeLecteur.
     */
    fun setVolume(fraction: Float) {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val f = fraction.coerceIn(0f, 1f)
        _volume.value = f
        if (max <= 0) return
        val target = f * max
        val step = ceil(target).toInt().coerceIn(0, max)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0) }
        lastSystemStep = step
        controller?.volume = if (step > 0) (target / step).coerceIn(0f, 1f) else 1f
    }

    /** Ajuste le volume perçu d'un delta (fraction 0..1). */
    fun adjustVolume(delta: Float) = setVolume(_volume.value + delta)

    /** Icônes ± du lecteur : un DEMI-PAS système (= moitié de l'incrément des touches physiques). */
    fun nudgeVolume(up: Boolean) {
        val max = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0).coerceAtLeast(1)
        val half = 0.5f / max
        adjustVolume(if (up) half else -half)
    }

    /**
     * Relit le volume média système. Si le pas a changé EN DEHORS de l'app (touches physiques), on
     * réinitialise le réglage fin du lecteur et on resynchronise la barre sur ce pas plein.
     */
    fun refreshVolume() {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) { _volume.value = 0f; return }
        val s = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (s != lastSystemStep) {
            controller?.volume = 1f
            lastSystemStep = s
            _volume.value = s / max.toFloat()
        }
    }

    /** Saute au morceau d'index `index` dans la file. */
    fun playIndex(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0L)
        c.play()
    }

    /** Vide la file d'attente (arrête la lecture). */
    fun clearQueue() {
        controller?.clearMediaItems()
        _queue.value = emptyList()
        _current.value = null
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
