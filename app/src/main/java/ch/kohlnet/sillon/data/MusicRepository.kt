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
import java.util.UUID

/** Modèle d'album côté UI. `serverId` = serveur d'origine (multi-serveur). */
@Serializable
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val serverId: String = "",
)

/** Morceau côté UI. `serverId` = serveur d'origine. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val index: Int?,
    val durationMs: Long?,
    val streamUrl: String,
    val coverUrl: String?,
    val serverId: String = "",
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

/** État de l'opération « ajouter un serveur », observé par l'UI. */
sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Connecting : ConnectionStatus
    data class Connected(val name: String) : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

/**
 * Source de vérité MULTI-SERVEUR. Agrège la bibliothèque de tous les serveurs actifs (Jellyfin,
 * Subsonic/Navidrome…). Chaque album/morceau porte son `serverId` → tracks/lyrics routés vers le bon
 * provider. Lecture seule côté serveurs ; favoris en local. Persistance via [ServerStore].
 */
object MusicRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var initialized = false

    /** Providers des serveurs ACTIFS, indexés par serverId. */
    private val providers = mutableMapOf<String, ServerProvider>()

    private val _servers = MutableStateFlow<List<ServerConfig>>(emptyList())
    val servers: StateFlow<List<ServerConfig>> = _servers.asStateFlow()

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _favorites = MutableStateFlow<List<Album>>(emptyList())
    val favorites: StateFlow<List<Album>> = _favorites.asStateFlow()

    /** À appeler une fois au lancement (MainActivity). Restaure serveurs + favoris. */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        scope.launch { _favorites.value = FavoritesStore.load(context.applicationContext) }
        scope.launch {
            _servers.value = ServerStore.load(context.applicationContext)
            rebuildProviders()
            loadAlbums()
        }
    }

    /** (Re)construit la map des providers à partir des serveurs actifs. */
    private fun rebuildProviders() {
        val active = _servers.value.filter { it.active }
        // ferme ceux qui ne sont plus actifs
        providers.keys.toList().filter { id -> active.none { it.id == id } }
            .forEach { providers.remove(it)?.close() }
        // crée les nouveaux
        active.forEach { cfg -> if (!providers.containsKey(cfg.id)) providers[cfg.id] = providerFor(cfg) }
    }

    /** Ajoute un serveur (authentifie, persiste, recharge). */
    fun addServer(type: ServerType, url: String, username: String, password: String) {
        scope.launch {
            _status.value = ConnectionStatus.Connecting
            try {
                val config = authenticateServer(UUID.randomUUID().toString(), type, url, username, password)
                val updated = _servers.value + config
                _servers.value = updated
                appContext?.let { ServerStore.save(it, updated) }
                rebuildProviders()
                _status.value = ConnectionStatus.Connected(config.name)
                loadAlbums()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = ConnectionStatus.Error(e.message ?: "Échec de la connexion")
            }
        }
    }

    /** Retire un serveur. */
    fun removeServer(id: String) {
        scope.launch {
            providers.remove(id)?.close()
            val updated = _servers.value.filterNot { it.id == id }
            _servers.value = updated
            appContext?.let { ServerStore.save(it, updated) }
            loadAlbums()
        }
    }

    /** Active/désactive un serveur dans la bibliothèque agrégée. */
    fun setActive(id: String, active: Boolean) {
        scope.launch {
            val updated = _servers.value.map { if (it.id == id) it.copy(active = active) else it }
            _servers.value = updated
            appContext?.let { ServerStore.save(it, updated) }
            rebuildProviders()
            loadAlbums()
        }
    }

    /** Ajoute/retire un album des favoris (local). */
    fun toggleFavorite(album: Album) {
        val current = _favorites.value
        val updated = if (current.any { it.id == album.id && it.serverId == album.serverId }) {
            current.filterNot { it.id == album.id && it.serverId == album.serverId }
        } else {
            current + album
        }
        _favorites.value = updated
        appContext?.let { ctx -> scope.launch { FavoritesStore.save(ctx, updated) } }
    }

    fun isFavorite(album: Album): Boolean =
        _favorites.value.any { it.id == album.id && it.serverId == album.serverId }

    /** Recharge les albums récents agrégés de tous les serveurs actifs. */
    suspend fun loadAlbums() {
        _albums.value = aggregate { it.recentAlbums() }
    }

    /** Recherche agrégée (nom d'album + artiste) sur tous les serveurs actifs. */
    suspend fun searchAlbums(query: String): List<Album> = aggregate { it.searchAlbums(query) }

    /** Albums d'un artiste (par nom), agrégés. */
    suspend fun albumsByArtistName(name: String): List<Album> = aggregate { it.albumsByArtistName(name) }

    /** Morceaux d'un album — routés vers le provider du serveur d'origine. */
    suspend fun tracks(album: Album): List<Track> =
        providers[album.serverId]?.let { runCatching { it.tracks(album.id) }.getOrDefault(emptyList()) } ?: emptyList()

    /** Paroles d'un morceau — routées vers le provider du serveur d'origine. */
    suspend fun lyrics(track: Track): TrackLyrics? =
        providers[track.serverId]?.let { runCatching { it.lyrics(track.id) }.getOrNull() }

    /** Exécute `block` sur chaque provider actif et fusionne (dédoublonné par serveur+id). */
    private suspend fun aggregate(block: suspend (ServerProvider) -> List<Album>): List<Album> {
        val result = mutableListOf<Album>()
        for (p in providers.values.toList()) {
            result += runCatching { block(p) }.getOrDefault(emptyList())
        }
        return result.distinctBy { it.serverId + "|" + it.id }
    }
}
