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
import ch.kohlnet.sillon.widget.SillonWidget
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

    /** File ACTIVE d'ExoPlayer (ce qui est réellement joué) — peut être l'album OU la file d'attente. */
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    /**
     * FILE D'ATTENTE persistante (mix manuel de l'utilisateur), INDÉPENDANTE de la file active : elle
     * survit aux bascules d'onglet Album↔File. C'est ce que montre l'onglet « File d'attente ». L'onglet
     * sélectionné décide laquelle (album ou cette file) est la file ACTIVE → quel est le « suivant ».
     */
    private val _fileQueue = MutableStateFlow<List<Track>>(emptyList())
    val fileQueue: StateFlow<List<Track>> = _fileQueue.asStateFlow()

    private fun keyOf(t: Track) = "${t.serverId}/${t.id}"
    private fun sameTracks(a: List<Track>, b: List<Track>) =
        a.size == b.size && a.indices.all { keyOf(a[it]) == keyOf(b[it]) }

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    /** Volume MÉDIA du système (0..1) — à fond = vraiment fort. Réglé par la barre du lecteur. */
    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()
    private var audioManager: AudioManager? = null

    /** `Player.REPEAT_MODE_OFF` / `_ALL` / `_ONE`. */
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    /** Contexte applicatif (pour rafraîchir le widget « Lecture en cours »). */
    private var appContext: Context? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            appContext?.let { SillonWidget.update(it) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val t = _queue.value.getOrNull(controller?.currentMediaItemIndex ?: -1)
            _current.value = t
            if (t != null) PlayHistory.record(t) // historique d'écoute (accueil : plus écoutés / récemment écoutés)
            appContext?.let { SillonWidget.update(it) }
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
        appContext = ctx
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

    /**
     * Démarre la lecture d'une liste à partir de `startIndex`. `resetFile` (défaut vrai) réinitialise la
     * FILE D'ATTENTE sur cette liste (nouvelle lecture depuis un album/une playlist) ; à `false`, la file
     * manuelle est PRÉSERVÉE (cas d'un tap dans l'onglet Album/File du lecteur, qui ne fait que basculer
     * la file active et démarrer un titre).
     */
    fun play(tracks: List<Track>, startIndex: Int, resetFile: Boolean = true) {
        val c = controller ?: return
        _queue.value = tracks
        if (resetFile) _fileQueue.value = tracks
        c.setMediaItems(tracks.map(::mediaItem), startIndex, 0L)
        c.prepare()
        c.play()
        _current.value = tracks.getOrNull(startIndex)
    }

    /**
     * Bascule la file ACTIVE d'ExoPlayer vers `tracks` en CONSERVANT le morceau courant et sa position
     * (changement d'onglet Album↔File dans le lecteur). No-op si déjà active → pas de reconstruction
     * inutile pendant la lecture normale.
     */
    fun useQueue(tracks: List<Track>) {
        val c = controller ?: return
        if (tracks.isEmpty() || sameTracks(_queue.value, tracks)) return
        val cur = _current.value
        var idx = if (cur != null) tracks.indexOfFirst { keyOf(it) == keyOf(cur) } else -1
        if (idx < 0 && cur != null) idx = tracks.indexOfFirst { it.matchKey() == cur.matchKey() }
        if (cur != null && idx < 0) return // morceau courant absent de la cible → on ne bascule pas (évite un saut)
        if (idx < 0) idx = 0
        val wasPlaying = c.isPlaying
        val pos = c.currentPosition.coerceAtLeast(0)
        _queue.value = tracks
        c.setMediaItems(tracks.map(::mediaItem), idx, pos)
        c.prepare()
        if (wasPlaying) c.play()
    }

    /** Ajoute un titre À LA FIN de la file d'attente (manuelle). Démarre la lecture si tout est vide. */
    fun addToQueue(track: Track) {
        val c = controller ?: return
        if (_fileQueue.value.isEmpty()) { play(listOf(track), 0); return }
        val fileActive = sameTracks(_queue.value, _fileQueue.value)
        _fileQueue.value = _fileQueue.value + track
        if (fileActive) { // la file est la liste jouée → on répercute dans ExoPlayer
            c.addMediaItem(mediaItem(track))
            _queue.value = _queue.value + track
        }
    }

    /** Insère un titre JUSTE APRÈS le morceau courant dans la file d'attente (« Lire ensuite »). */
    fun playNext(track: Track) {
        val c = controller ?: return
        if (_fileQueue.value.isEmpty()) { play(listOf(track), 0); return }
        val fileActive = sameTracks(_queue.value, _fileQueue.value)
        val cur = _current.value
        val fi = if (cur != null) _fileQueue.value.indexOfFirst { keyOf(it) == keyOf(cur) } else -1
        val at = (if (fi >= 0) fi + 1 else _fileQueue.value.size).coerceIn(0, _fileQueue.value.size)
        _fileQueue.value = _fileQueue.value.toMutableList().also { it.add(at, track) }
        if (!fileActive) return
        val exoAt = (c.currentMediaItemIndex + 1).coerceIn(0, _queue.value.size)
        c.addMediaItem(exoAt, mediaItem(track))
        _queue.value = _queue.value.toMutableList().also { it.add(exoAt, track) }
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
        _fileQueue.value = emptyList()
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
