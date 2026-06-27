package ch.kohlnet.sillon.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Modèle d'album côté UI (indépendant du serveur). */
@Serializable
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
)

/** Morceau côté UI. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val index: Int?,
    val durationMs: Long?,
    val streamUrl: String,
    val coverUrl: String?,
)

/** Une ligne de paroles. `timeSeconds` non-nil = paroles synchronisées. */
data class LyricLine(val text: String, val timeSeconds: Double?)

/** Paroles d'un morceau. `synced` = au moins une ligne horodatée. */
data class TrackLyrics(val synced: Boolean, val lines: List<LyricLine>) {
    /** Index de la ligne en cours à l'instant `t` (s) : dernière ligne horodatée <= t. */
    fun activeLineIndex(at: Double): Int? {
        var best: Int? = null
        var bestTime = Double.NEGATIVE_INFINITY
        lines.forEachIndexed { i, line ->
            val lt = line.timeSeconds ?: return@forEachIndexed
            if (lt <= at && lt >= bestTime) { bestTime = lt; best = i }
        }
        return best
    }
}

/** État de la connexion au serveur, observé par l'UI. */
sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Connecting : ConnectionStatus
    data class Connected(val userName: String) : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

/**
 * Source de vérité unique pour la connexion serveur + la bibliothèque. Singleton. La connexion est
 * persistée via [ServerStore] (URL + jeton, jamais le mot de passe) : au lancement, on restaure et on
 * recharge les albums automatiquement.
 */
object MusicRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var initialized = false

    private var client: JellyfinClient? = null
    private var token: String? = null
    private var userId: String? = null

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _favorites = MutableStateFlow<List<Album>>(emptyList())
    val favorites: StateFlow<List<Album>> = _favorites.asStateFlow()

    /** À appeler une fois au lancement (MainActivity). Restaure la connexion persistée si présente. */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        scope.launch { _favorites.value = FavoritesStore.load(context.applicationContext) }
        scope.launch {
            val saved = ServerStore.load(context.applicationContext) ?: return@launch
            client = JellyfinClient(saved.baseUrl)
            token = saved.token
            userId = saved.userId
            _status.value = ConnectionStatus.Connected(saved.username)
            try {
                loadAlbums()
            } catch (e: Exception) {
                // Jeton expiré / serveur injoignable : on oublie la session restaurée.
                disconnect()
            }
        }
    }

    /**
     * Authentifie, persiste la connexion, puis charge les albums. Lancé sur le scope DURABLE du dépôt
     * (et non celui de l'écran) : la connexion survit à un changement d'écran / une recomposition.
     */
    fun connect(url: String, username: String, password: String) {
        scope.launch {
            _status.value = ConnectionStatus.Connecting
            try {
                client?.close()
                val c = JellyfinClient(url)
                val auth = c.authenticate(username, password)
                client = c
                token = auth.accessToken
                userId = auth.user.id
                _status.value = ConnectionStatus.Connected(auth.user.name)
                appContext?.let {
                    ServerStore.save(it, SavedServer(c.base, auth.user.id, auth.accessToken, auth.user.name))
                }
                loadAlbums()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                client = null
                token = null
                userId = null
                _albums.value = emptyList()
                _status.value = ConnectionStatus.Error(e.message ?: "Échec de la connexion")
            }
        }
    }

    /** Oublie la connexion (efface aussi la session persistée). */
    fun disconnect() {
        scope.launch { appContext?.let { ServerStore.clear(it) } }
        client?.close()
        client = null
        token = null
        userId = null
        _albums.value = emptyList()
        _status.value = ConnectionStatus.Idle
    }

    /** Ajoute/retire un album des favoris (local). */
    fun toggleFavorite(album: Album) {
        val current = _favorites.value
        val updated = if (current.any { it.id == album.id }) {
            current.filterNot { it.id == album.id }
        } else {
            current + album
        }
        _favorites.value = updated
        appContext?.let { ctx -> scope.launch { FavoritesStore.save(ctx, updated) } }
    }

    /** Recharge la liste des albums depuis le serveur connecté. */
    suspend fun loadAlbums() {
        val c = client ?: return
        val t = token ?: return
        val u = userId ?: return
        val items = c.albums(t, u)
        _albums.value = items.map { item ->
            Album(
                id = item.id,
                title = item.name,
                artist = item.albumArtist.orEmpty(),
                coverUrl = c.coverUrl(item.id, t),
            )
        }
    }

    /** Paroles d'un morceau (ou null si absentes). */
    suspend fun lyrics(trackId: String): TrackLyrics? {
        val c = client ?: return null
        val t = token ?: return null
        val dto = c.lyrics(t, trackId) ?: return null
        val lines = dto.lyrics.map { LyricLine(it.text, it.start?.let { ticks -> ticks / 10_000_000.0 }) }
        if (lines.isEmpty()) return null
        return TrackLyrics(synced = lines.any { it.timeSeconds != null }, lines = lines)
    }

    /** Recherche d'albums (par NOM d'album ET par ARTISTE) sur le serveur connecté. */
    suspend fun searchAlbums(query: String): List<Album> {
        val c = client ?: return emptyList()
        val t = token ?: return emptyList()
        val u = userId ?: return emptyList()
        val byName = c.searchAlbums(t, u, query)
        val byArtist = c.searchArtists(t, u, query).flatMap {
            runCatching { c.albumsByArtist(t, u, it.id) }.getOrDefault(emptyList())
        }
        return (byName + byArtist).distinctBy { it.id }.map { item ->
            Album(item.id, item.name, item.albumArtist.orEmpty(), c.coverUrl(item.id, t))
        }
    }

    /** Albums d'un artiste (recherché par son nom). */
    suspend fun albumsByArtistName(name: String): List<Album> {
        val c = client ?: return emptyList()
        val t = token ?: return emptyList()
        val u = userId ?: return emptyList()
        val artists = c.searchArtists(t, u, name)
        val artist = artists.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: artists.firstOrNull() ?: return emptyList()
        return c.albumsByArtist(t, u, artist.id).map { item ->
            Album(item.id, item.name, item.albumArtist.orEmpty(), c.coverUrl(item.id, t))
        }
    }

    /** Morceaux d'un album (dans l'ordre des pistes), avec leur URL de flux. */
    suspend fun tracks(albumId: String): List<Track> {
        val c = client ?: return emptyList()
        val t = token ?: return emptyList()
        val u = userId ?: return emptyList()
        return c.albumTracks(t, u, albumId).map { tk ->
            Track(
                id = tk.id,
                title = tk.name,
                artist = tk.artists?.joinToString(", ").orEmpty(),
                index = tk.index,
                durationMs = tk.runTimeTicks?.div(10_000),
                streamUrl = c.streamUrl(tk.id, t),
                coverUrl = c.coverUrl(tk.id, t),
            )
        }
    }
}
