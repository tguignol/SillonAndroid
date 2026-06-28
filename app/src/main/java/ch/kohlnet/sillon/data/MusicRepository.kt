package ch.kohlnet.sillon.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Modèle d'album côté UI. `serverId` = serveur représentant (pour router tracks/lyrics).
 * `sources` = tous les serveurs qui possèdent cet album (déduplication inter-serveurs → badge source).
 */
@Serializable
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val serverId: String = "",
    val sources: List<String> = emptyList(),
) {
    /** Clé de correspondance inter-serveurs (dédup + favoris propagés) : titre+artiste normalisés. */
    fun matchKey(): String = (title + " " + artist).trim().lowercase().replace(Regex("\\s+"), " ")
}

/** Morceau côté UI. `serverId` = serveur d'origine. Champs qualité audio (codec/fréquence/profondeur/débit). */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val index: Int?,
    val durationMs: Long?,
    val streamUrl: String,
    val coverUrl: String?,
    val serverId: String = "",
    val format: String? = null,       // ex "flac", "alac", "wav"
    val sampleRateHz: Int? = null,
    val bitDepthBits: Int? = null,
    val bitrateKbps: Int? = null,
) {
    /** Clé de correspondance (favoris pistes propagés entre serveurs) : titre+artiste normalisés. */
    fun matchKey(): String = (title + " " + artist).trim().lowercase().replace(Regex("\\s+"), " ")

    /** Libellé qualité condensé façon iOS : « FLAC · 44,1 kHz » (codec en majuscule + fréquence). */
    fun qualityLabel(): String? {
        val codec = format?.takeIf { it.isNotBlank() }?.uppercase()
        val khz = sampleRateHz?.takeIf { it > 0 }?.let { hz ->
            val k = hz / 1000.0
            if (k == k.toLong().toDouble()) k.toLong().toString()
            else String.format("%.1f", k).replace(".", ",")
        }
        return when {
            codec != null && khz != null -> "$codec · $khz kHz"
            codec != null -> codec
            khz != null -> "$khz kHz"
            else -> null
        }
    }
}

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

    /** Pistes favorites (clés titre+artiste normalisées, propagées entre serveurs). */
    private val KEY_FAV_TRACKS = stringPreferencesKey("favoriteTrackKeys")
    private val _favoriteTrackKeys = MutableStateFlow<Set<String>>(emptySet())
    val favoriteTrackKeys: StateFlow<Set<String>> = _favoriteTrackKeys.asStateFlow()

    /** Vrai pendant un rechargement GLOBAL de la bibliothèque (bouton « Rafraîchir »). */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Id du serveur en cours de rafraîchissement INDIVIDUEL (ou null). */
    private val _refreshingServerId = MutableStateFlow<String?>(null)
    val refreshingServerId: StateFlow<String?> = _refreshingServerId.asStateFlow()

    /** À appeler une fois au lancement (MainActivity). Restaure serveurs + favoris. */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        scope.launch { _favorites.value = FavoritesStore.load(context.applicationContext) }
        scope.launch {
            val raw = context.applicationContext.dataStore.data.first()[KEY_FAV_TRACKS]
            _favoriteTrackKeys.value = raw?.split("\n")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        }
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

    /** Monte/descend un serveur dans l'ordre de PRIORITÉ d'affichage (dédup → le plus haut gagne). */
    fun moveServer(id: String, up: Boolean) {
        scope.launch {
            val list = _servers.value.toMutableList()
            val i = list.indexOfFirst { it.id == id }
            if (i < 0) return@launch
            val j = if (up) i - 1 else i + 1
            if (j < 0 || j >= list.size) return@launch
            list[i] = list[j].also { list[j] = list[i] }
            _servers.value = list
            appContext?.let { ServerStore.save(it, list) }
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

    /** Ajoute/retire un album des favoris (local). Correspondance par titre+artiste → un favori
     *  s'applique à toutes les copies (favoris PROPAGÉS entre serveurs). */
    fun toggleFavorite(album: Album) {
        val current = _favorites.value
        val key = album.matchKey()
        val updated = if (current.any { it.matchKey() == key }) {
            current.filterNot { it.matchKey() == key }
        } else {
            current + album
        }
        _favorites.value = updated
        appContext?.let { ctx -> scope.launch { FavoritesStore.save(ctx, updated) } }
    }

    fun isFavorite(album: Album): Boolean =
        _favorites.value.any { it.matchKey() == album.matchKey() }

    /** Ajoute/retire la piste courante des favoris (local, propagé par titre+artiste). */
    fun toggleTrackFavorite(track: Track) {
        val key = track.matchKey()
        val cur = _favoriteTrackKeys.value
        val updated = if (key in cur) cur - key else cur + key
        _favoriteTrackKeys.value = updated
        appContext?.let { ctx -> scope.launch { ctx.dataStore.edit { it[KEY_FAV_TRACKS] = updated.joinToString("\n") } } }
    }

    fun isTrackFavorite(track: Track): Boolean = track.matchKey() in _favoriteTrackKeys.value

    /** Recharge TOUTE la bibliothèque agrégée de tous les serveurs actifs (paginée). */
    suspend fun loadAlbums() {
        _albums.value = aggregate { it.allAlbums() }
    }

    /** Rechargement GLOBAL (bouton « Rafraîchir ») : recrée les providers actifs et relit la bibliothèque. */
    fun refresh() {
        scope.launch {
            _refreshing.value = true
            try {
                rebuildProviders()
                loadAlbums()
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Rechargement d'UN serveur : recrée son provider (reconnexion) puis relit la bibliothèque agrégée. */
    fun refreshServer(id: String) {
        scope.launch {
            _refreshingServerId.value = id
            try {
                _servers.value.firstOrNull { it.id == id && it.active }?.let { cfg ->
                    providers.remove(id)?.close()
                    providers[id] = providerFor(cfg)
                }
                loadAlbums()
            } finally {
                _refreshingServerId.value = null
            }
        }
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

    /**
     * Exécute `block` sur chaque provider actif (DANS L'ORDRE DE PRIORITÉ = ordre de la liste serveurs),
     * fusionne et DÉDOUBLONNE inter-serveurs. La dédup garde le 1er rencontré → le serveur le plus haut
     * dans la liste gagne sur les doublons (cf. [moveServer]).
     */
    private suspend fun aggregate(block: suspend (ServerProvider) -> List<Album>): List<Album> {
        val ordered = _servers.value.filter { it.active }.mapNotNull { providers[it.id] }
        val result = mutableListOf<Album>()
        for (p in ordered) {
            result += runCatching { block(p) }.getOrDefault(emptyList())
        }
        return dedup(result)
    }

    /**
     * Déduplication inter-serveurs : un même album présent sur plusieurs serveurs → UNE entrée
     * (représentant = 1er rencontré), avec la liste de ses serveurs source. Clé = titre+artiste
     * normalisés. (Sans effet à l'intérieur d'un seul serveur.)
     */
    private fun dedup(albums: List<Album>): List<Album> {
        val groups = LinkedHashMap<String, MutableList<Album>>()
        for (a in albums) {
            val key = a.title.normalizedKey() + " " + a.artist.normalizedKey()
            groups.getOrPut(key) { mutableListOf() }.add(a)
        }
        return groups.values.map { group ->
            group.first().copy(sources = group.map { it.serverId }.distinct())
        }
    }

    private fun String.normalizedKey() = trim().lowercase().replace(Regex("\\s+"), " ")
}
