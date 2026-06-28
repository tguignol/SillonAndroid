package ch.kohlnet.sillon.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
    val year: Int? = null,
    val genre: String? = null,
) {
    /** Clé de correspondance inter-serveurs (dédup + favoris propagés) : titre+artiste normalisés. */
    fun matchKey(): String = (title + " " + artist).trim().lowercase().replace(Regex("\\s+"), " ")
}

/** Morceau côté UI. `serverId` = serveur d'origine. Champs qualité audio (codec/fréquence/profondeur/débit). */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val index: Int?,
    val durationMs: Long?,
    val streamUrl: String,
    val coverUrl: String?,
    val serverId: String = "",
    val album: String? = null,
    val format: String? = null,       // ex "flac", "alac", "wav"
    val sampleRateHz: Int? = null,
    val bitDepthBits: Int? = null,
    val bitrateKbps: Int? = null,
    val disc: Int? = null,            // n° de disque (albums multi-disques) ; null/1 = disque unique
) {
    /** Clé de correspondance (favoris pistes propagés entre serveurs) : titre+artiste normalisés. */
    fun matchKey(): String = (title + " " + artist).trim().lowercase().replace(Regex("\\s+"), " ")

    /** Libellé du format pour l'affichage : M4A lossless → « ALAC », M4A lossy → « AAC », sinon majuscule. */
    fun formatLabel(): String? {
        val f = format?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            f == "alac" -> "ALAC"
            f in setOf("m4a", "m4b", "mp4", "mp4a") -> if (bitDepthBits != null) "ALAC" else "AAC"
            else -> f.uppercase()
        }
    }

    /** Libellé qualité condensé façon iOS : « FLAC · 44,1 kHz » (codec + fréquence). */
    fun qualityLabel(): String? {
        val codec = formatLabel()
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

    /** Vrai pendant le chargement de la bibliothèque (affiche un indicateur au lieu de « Aucun album »). */
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _favorites = MutableStateFlow<List<Album>>(emptyList())
    val favorites: StateFlow<List<Album>> = _favorites.asStateFlow()

    /**
     * Favoris VISIBLES dans les listes (Accueil « Albums préférés », onglet Favoris) : uniquement ceux
     * encore disponibles sur un serveur ACTIF (matchKey présent dans la biblio active). Un favori dont
     * la seule source est désactivée disparaît des listes — et réapparaît si on réactive le serveur
     * (non destructif). `favorites` (brut) reste utilisé pour l'état du cœur ❤️.
     */
    val visibleFavorites: StateFlow<List<Album>> =
        combine(_favorites, _albums) { favs, albums ->
            val keys = albums.mapTo(HashSet()) { it.matchKey() }
            favs.filter { it.matchKey() in keys }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

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
        scope.launch { TrackCache.load(context.applicationContext).forEach { trackCache[it.key] = it } }
        scope.launch {
            val raw = context.applicationContext.dataStore.data.first()[KEY_FAV_TRACKS]
            _favoriteTrackKeys.value = raw?.split("\n")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        }
        scope.launch {
            _servers.value = ServerStore.load(context.applicationContext)
            rebuildProviders()
            // 1) Affichage INSTANTANÉ depuis le cache local (aucun réseau) : la dernière biblio connue.
            val cached = LibraryCache.load(context.applicationContext)
            if (cached.isNotEmpty()) {
                serverAlbums.putAll(cached)
                serverAlbums.keys.retainAll(_servers.value.map { it.id }.toSet())
                recomputeAlbums()
            }
            // 2) Rafraîchissement réseau EN FOND → met à jour la biblio + réécrit le cache disque.
            loadAlbums()
        }
    }

    /** (Re)construit la map des providers à partir des serveurs actifs. */
    private fun rebuildProviders() {
        val ctx = appContext ?: return
        val active = _servers.value.filter { it.active }
        // ferme ceux qui ne sont plus actifs
        providers.keys.toList().filter { id -> active.none { it.id == id } }
            .forEach { providers.remove(it)?.close() }
        // crée les nouveaux
        active.forEach { cfg -> if (!providers.containsKey(cfg.id)) providers[cfg.id] = providerFor(cfg, ctx) }
    }

    /** Ajoute une source de FICHIERS LOCAUX (dossier choisi via SAF). `treeUri` = URI d'arbre persistée. */
    fun addLocalServer(treeUri: String) {
        scope.launch {
            val ctx = appContext ?: return@launch
            _status.value = ConnectionStatus.Connecting
            val name = LocalProvider.folderName(ctx, treeUri) ?: "Dossier"
            val config = ServerConfig(
                id = UUID.randomUUID().toString(),
                type = ServerType.LOCAL,
                name = "$name · Local",
                baseUrl = treeUri,
                username = "",
            )
            val updated = _servers.value + config
            _servers.value = updated
            ServerStore.save(ctx, updated)
            rebuildProviders()
            _status.value = ConnectionStatus.Connected(config.name)
            loadAlbums()
        }
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

    /**
     * Modifie un serveur EXISTANT (nom/adresse/utilisateur, et mot de passe optionnel).
     * - mot de passe FOURNI → re-validation complète (ré-authentification) ; en cas d'échec, rien n'est modifié.
     * - mot de passe VIDE → mise à jour des métadonnées seulement (jeton/mot de passe conservés).
     * Conserve l'id, l'état actif et la position (priorité).
     */
    fun updateServer(id: String, name: String, baseUrl: String, username: String, password: String) {
        scope.launch {
            val existing = _servers.value.firstOrNull { it.id == id } ?: return@launch
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val finalName = name.trim().ifBlank { existing.name }
            if (password.isNotBlank()) {
                _status.value = ConnectionStatus.Connecting
                try {
                    val authed = authenticateServer(id, existing.type, cleanUrl, username, password)
                    applyConfig(id, authed.copy(name = finalName, active = existing.active))
                    _status.value = ConnectionStatus.Connected(finalName)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _status.value = ConnectionStatus.Error(e.message ?: "Échec de la connexion")
                }
            } else {
                applyConfig(id, existing.copy(name = finalName, baseUrl = cleanUrl, username = username))
            }
        }
    }

    /** Remplace la config d'un serveur (même position), recrée son provider et relit la bibliothèque. */
    private suspend fun applyConfig(id: String, newCfg: ServerConfig) {
        val updated = _servers.value.map { if (it.id == id) newCfg else it }
        _servers.value = updated
        appContext?.let { ServerStore.save(it, updated) }
        providers.remove(id)?.close()
        rebuildProviders()
        loadAlbums()
    }

    /** Retire un serveur. */
    fun removeServer(id: String) {
        scope.launch {
            providers.remove(id)?.close()
            serverAlbums.remove(id)
            clearTrackCacheFor(id)
            val updated = _servers.value.filterNot { it.id == id }
            _servers.value = updated
            appContext?.let { ServerStore.save(it, updated) }
            recomputeAlbums()
            persistLibraryCache()
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
            recomputeAlbums() // l'ordre change la dédup, pas besoin de re-télécharger
        }
    }

    /** Active/désactive un serveur dans la bibliothèque agrégée (retrait/affichage INSTANTANÉ). */
    fun setActive(id: String, active: Boolean) {
        scope.launch {
            val updated = _servers.value.map { if (it.id == id) it.copy(active = active) else it }
            _servers.value = updated
            appContext?.let { ServerStore.save(it, updated) }
            rebuildProviders()
            // Réactivé sans cache → récupérer juste ce serveur ; désactivation/cache présent → instantané.
            if (active && serverAlbums[id] == null) {
                providers[id]?.let { p -> runCatching { p.allAlbums() }.getOrNull()?.let { serverAlbums[id] = it } }
                persistLibraryCache()
            }
            recomputeAlbums()
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

    /** Cache des albums BRUTS par serveur (avant dédup) → (dés)activer/réordonner SANS re-télécharger. */
    private val serverAlbums = mutableMapOf<String, List<Album>>()

    /** Cache LOCAL des pistes par album (clé `serverId/albumId`) → ouverture d'album instantanée au 2ᵉ accès. */
    private val trackCache = mutableMapOf<String, TrackCache.Entry>()
    private var trackPersistJob: Job? = null

    /** Recharge TOUTE la bibliothèque des serveurs actifs (paginée) et met à jour le cache par serveur. */
    suspend fun loadAlbums() {
        // Loader BLOQUANT seulement si rien n'est encore affiché (1er lancement, cache vide). Si le cache
        // local a déjà peuplé la biblio, le rafraîchissement réseau est SILENCIEUX (pas de spinner).
        val showLoader = _albums.value.isEmpty()
        if (showLoader) _loading.value = true
        try {
            val ordered = _servers.value.filter { it.active }.mapNotNull { cfg -> providers[cfg.id]?.let { cfg.id to it } }
            val fetched = coroutineScope {
                ordered.map { (id, p) -> async { id to runCatching { p.allAlbums() }.getOrNull() } }.awaitAll()
            }
            // On ne remplace le cache d'un serveur QUE si la requête a RÉUSSI (null = échec réseau) →
            // un serveur momentanément injoignable conserve sa dernière biblio connue (pas d'écrasement).
            fetched.forEach { (id, list) -> if (list != null) serverAlbums[id] = list }
            serverAlbums.keys.retainAll(_servers.value.map { it.id }.toSet())
            recomputeAlbums()
            persistLibraryCache()
        } finally {
            if (showLoader) _loading.value = false
        }
    }

    /** Réécrit le cache local des métadonnées (albums par serveur) sur disque, en tâche de fond. */
    private fun persistLibraryCache() {
        val ctx = appContext ?: return
        val snapshot = serverAlbums.toMap()
        scope.launch { LibraryCache.save(ctx, snapshot) }
    }

    /**
     * Reconstruit la bibliothèque agrégée DEPUIS LE CACHE, en ne gardant que les serveurs ACTIFS
     * (dans l'ordre de priorité → dédup correcte). INSTANTANÉ (aucun réseau) : une (dés)activation
     * ou un changement de priorité retire/affiche immédiatement les albums et badges concernés.
     */
    private fun recomputeAlbums() {
        val activeOrdered = _servers.value.filter { it.active }.map { it.id }
        _albums.value = dedup(activeOrdered.flatMap { id -> serverAlbums[id].orEmpty() })
    }

    /** Rechargement GLOBAL (bouton « Rafraîchir ») : recrée les providers actifs et relit la bibliothèque. */
    fun refresh() {
        scope.launch {
            _refreshing.value = true
            try {
                rebuildProviders()
                trackCache.clear()      // pistes re-téléchargées au prochain accès (fraîcheur)
                persistTrackCache()
                loadAlbums()
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Rechargement d'UN serveur : recrée son provider (reconnexion), re-télécharge SES albums, re-dédup. */
    fun refreshServer(id: String) {
        scope.launch {
            _refreshingServerId.value = id
            try {
                _servers.value.firstOrNull { it.id == id && it.active }?.let { cfg ->
                    appContext?.let { ctx ->
                        providers.remove(id)?.close()
                        val p = providerFor(cfg, ctx)
                        providers[id] = p
                        // Succès seulement → on garde la biblio connue si la reconnexion échoue.
                        runCatching { p.allAlbums() }.getOrNull()?.let { serverAlbums[id] = it }
                    }
                }
                clearTrackCacheFor(id)  // pistes de ce serveur re-téléchargées au prochain accès
                recomputeAlbums()
                persistLibraryCache()
            } finally {
                _refreshingServerId.value = null
            }
        }
    }

    /** Recherche agrégée (nom d'album + artiste) sur tous les serveurs actifs. */
    suspend fun searchAlbums(query: String): List<Album> = aggregate { it.searchAlbums(query) }

    /** Albums d'un artiste (par nom), agrégés. */
    suspend fun albumsByArtistName(name: String): List<Album> = aggregate { it.albumsByArtistName(name) }

    /**
     * Morceaux d'un album — routés vers le provider du serveur d'origine.
     * Sert le CACHE LOCAL instantanément si présent (ouverture d'album immédiate au 2ᵉ accès) ; sinon va
     * au réseau et met en cache. Les tracklists étant stables, on ne re-télécharge PAS à chaque ouverture :
     * la fraîcheur est reprise par « Rafraîchir » (qui purge le cache pistes). Le streaming, lui, va
     * toujours chercher le flux directement sur le serveur.
     */
    suspend fun tracks(album: Album): List<Track> {
        val key = "${album.serverId}/${album.id}"
        trackCache[key]?.let { hit ->
            // Cache touché → on rafraîchit juste l'horodatage LRU (persistance débouncée).
            trackCache[key] = hit.copy(lastAccess = System.currentTimeMillis())
            persistTrackCache()
            return hit.tracks
        }
        val fresh = providers[album.serverId]?.let { runCatching { it.tracks(album.id) }.getOrNull() } ?: emptyList()
        if (fresh.isNotEmpty()) {
            trackCache[key] = TrackCache.Entry(key, fresh, System.currentTimeMillis())
            evictTrackCacheIfNeeded()
            persistTrackCache()
        }
        return fresh
    }

    /** Borne le cache pistes (LRU) : retire les albums les moins récemment consultés au-delà du plafond. */
    private fun evictTrackCacheIfNeeded() {
        val over = trackCache.size - TrackCache.MAX_ALBUMS
        if (over <= 0) return
        trackCache.values.sortedBy { it.lastAccess }.take(over).forEach { trackCache.remove(it.key) }
    }

    /** Réécrit le cache pistes sur disque, DÉBOUNCÉ (coalesce les rafales lors d'un défilement). */
    private fun persistTrackCache() {
        val ctx = appContext ?: return
        trackPersistJob?.cancel()
        trackPersistJob = scope.launch {
            delay(2000)
            TrackCache.save(ctx, trackCache.values.toList())
        }
    }

    /** Purge les pistes en cache d'un serveur (clé préfixée `serverId/`) → re-téléchargées au prochain accès. */
    private fun clearTrackCacheFor(serverId: String) {
        trackCache.keys.filter { it.startsWith("$serverId/") }.forEach { trackCache.remove(it) }
        persistTrackCache()
    }

    /** Format audio représentatif d'un album (1re piste), récupéré à la volée et MIS EN CACHE. */
    private val formatCache = mutableMapOf<String, String?>()
    suspend fun albumFormat(album: Album): String? {
        if (formatCache.containsKey(album.id)) return formatCache[album.id]
        val fmt = runCatching { tracks(album).firstOrNull()?.formatLabel() }.getOrNull()
        formatCache[album.id] = fmt
        return fmt
    }

    /** Paroles d'un morceau — routées vers le provider du serveur d'origine. */
    suspend fun lyrics(track: Track): TrackLyrics? =
        providers[track.serverId]?.let { runCatching { it.lyrics(track.id) }.getOrNull() }

    /**
     * Exécute `block` sur chaque provider actif (DANS L'ORDRE DE PRIORITÉ = ordre de la liste serveurs),
     * fusionne et DÉDOUBLONNE inter-serveurs. La dédup garde le 1er rencontré → le serveur le plus haut
     * dans la liste gagne sur les doublons (cf. [moveServer]).
     */
    private suspend fun aggregate(block: suspend (ServerProvider) -> List<Album>): List<Album> = coroutineScope {
        val ordered = _servers.value.filter { it.active }.mapNotNull { providers[it.id] }
        // Serveurs interrogés EN PARALLÈLE (overlap des temps réseau) ; l'ordre de la liste est
        // préservé (map→async→awaitAll) → la dédup garde toujours le serveur prioritaire.
        val results = ordered.map { p -> async { runCatching { block(p) }.getOrDefault(emptyList()) } }.awaitAll()
        dedup(results.flatten())
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
            group.first().copy(
                sources = group.map { it.serverId }.distinct(),
                year = group.firstNotNullOfOrNull { it.year },
                genre = group.firstNotNullOfOrNull { it.genre?.takeIf { g -> g.isNotBlank() } },
            )
        }
    }

    private fun String.normalizedKey() = trim().lowercase().replace(Regex("\\s+"), " ")
}
