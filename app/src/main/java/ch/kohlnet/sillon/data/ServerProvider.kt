package ch.kohlnet.sillon.data

import android.content.Context
import kotlinx.serialization.Serializable

/** Types de sources musicales prises en charge (comme l'iOS). `badge` = libellé court (badge source). */
enum class ServerType(val label: String, val badge: String) {
    JELLYFIN("Jellyfin", "Jellyfin"),
    SUBSONIC("Subsonic / Navidrome", "Navidrome"),
    LOCAL("Fichiers locaux", "Local"),
}

/**
 * Configuration d'un serveur (persistée). Selon le type, soit un jeton Jellyfin (token+userId),
 * soit le mot de passe Subsonic (pour dériver jeton+sel à chaque requête). `active` = pris en compte
 * dans la bibliothèque agrégée.
 *
 * (Stockage local ; à chiffrer plus tard façon Keychain — cf. note sécurité.)
 */
@Serializable
data class ServerConfig(
    val id: String,
    val type: ServerType,
    val name: String,
    val baseUrl: String,
    val username: String,
    val active: Boolean = true,
    val token: String? = null,    // Jellyfin
    val userId: String? = null,   // Jellyfin
    val password: String? = null, // Subsonic
)

/**
 * Interface commune à tous les serveurs (Jellyfin, Subsonic…). Chaque provider étiquette ses
 * `Album`/`Track` avec `config.id` (pour router tracks/lyrics/badge source).
 */
interface ServerProvider {
    val config: ServerConfig
    /** TOUTE la bibliothèque (paginée jusqu'au bout), récents d'abord. */
    suspend fun allAlbums(): List<Album>
    suspend fun searchAlbums(query: String): List<Album>
    suspend fun albumsByArtistName(name: String): List<Album>
    suspend fun tracks(albumId: String): List<Track>
    suspend fun lyrics(trackId: String): TrackLyrics?
    /** Playlists du serveur (LECTURE SEULE — jamais modifiées). Vide par défaut (ex. fichiers locaux). */
    suspend fun playlists(): List<ServerPlaylist> = emptyList()
    /** Morceaux d'une playlist serveur, dans l'ordre. Vide par défaut. */
    suspend fun playlistTracks(playlistId: String): List<Track> = emptyList()
    /** « Radio » à partir d'un titre : titres similaires/instant-mix. Vide par défaut. */
    suspend fun radio(seedTrackId: String): List<Track> = emptyList()
    fun close()
}

/** Playlist DISTANTE (serveur), lecture seule. `serverId` route les requêtes vers le bon provider. */
@Serializable
data class ServerPlaylist(
    val id: String,
    val name: String,
    val serverId: String,
    val coverUrl: String? = null,
    val trackCount: Int = 0,
)

/** Crée le provider correspondant à une config (le contexte ne sert qu'aux fichiers locaux). */
fun providerFor(config: ServerConfig, context: Context): ServerProvider = when (config.type) {
    ServerType.JELLYFIN -> JellyfinProvider(config)
    ServerType.SUBSONIC -> SubsonicProvider(config)
    ServerType.LOCAL -> LocalProvider(config, context.applicationContext)
}

/**
 * Authentifie un nouveau serveur et renvoie sa [ServerConfig] prête à persister (jette en cas d'échec).
 * `id` est généré par l'appelant.
 */
suspend fun authenticateServer(
    id: String,
    type: ServerType,
    url: String,
    username: String,
    password: String,
): ServerConfig = when (type) {
    ServerType.JELLYFIN -> JellyfinProvider.authenticate(id, url, username, password)
    ServerType.SUBSONIC -> SubsonicProvider.authenticate(id, url, username, password)
    ServerType.LOCAL -> error("Les fichiers locaux s'ajoutent via MusicRepository.addLocalServer")
}
