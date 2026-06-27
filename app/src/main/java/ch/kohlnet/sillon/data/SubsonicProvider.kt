package ch.kohlnet.sillon.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

// MARK: - DTOs Subsonic (réponses enveloppées dans "subsonic-response")

@Serializable
data class SubWrapper(@SerialName("subsonic-response") val response: SubResponse)

@Serializable
data class SubResponse(
    val status: String = "",
    val albumList2: SubAlbumList? = null,
    val searchResult3: SubSearchResult? = null,
    val album: SubAlbum? = null,
    val artist: SubArtist? = null,
    val lyricsList: SubLyricsList? = null,
)

@Serializable data class SubAlbumList(val album: List<SubAlbumItem> = emptyList())
@Serializable data class SubSearchResult(val album: List<SubAlbumItem> = emptyList(), val artist: List<SubArtistItem> = emptyList())
@Serializable data class SubAlbumItem(val id: String, val name: String = "", val artist: String = "", val coverArt: String? = null)
@Serializable data class SubArtistItem(val id: String, val name: String = "")
@Serializable data class SubArtist(val id: String = "", val name: String = "", val album: List<SubAlbumItem> = emptyList())
@Serializable data class SubAlbum(val id: String = "", val song: List<SubSong> = emptyList())
@Serializable data class SubSong(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val track: Int? = null,
    val duration: Int? = null, // secondes
    val coverArt: String? = null,
)
@Serializable data class SubLyricsList(val structuredLyrics: List<SubStructuredLyrics> = emptyList())
@Serializable data class SubStructuredLyrics(val synced: Boolean = false, val line: List<SubLyricLine> = emptyList())
@Serializable data class SubLyricLine(val start: Long? = null, val value: String = "")

/**
 * Provider Subsonic / Navidrome. Auth : jeton = md5(motDePasse + sel), sel aléatoire (réutilisé pour
 * toute l'instance — Subsonic l'accepte), passés en paramètres `u/t/s/v/c/f` à chaque requête `/rest/`.
 * Lecture seule.
 */
class SubsonicProvider(override val config: ServerConfig) : ServerProvider {
    private val base = config.baseUrl.trim().trimEnd('/')
    private val salt = generateSalt()
    private val token = md5(config.password.orEmpty() + salt)

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private suspend fun api(endpoint: String, params: Map<String, String> = emptyMap()): SubResponse =
        http.get("$base/rest/$endpoint") {
            parameter("u", config.username)
            parameter("t", token)
            parameter("s", salt)
            parameter("v", "1.16.1")
            parameter("c", "Sillon")
            parameter("f", "json")
            params.forEach { (k, v) -> parameter(k, v) }
        }.body<SubWrapper>().response

    private fun authQuery(): String {
        val u = URLEncoder.encode(config.username, "UTF-8")
        return "u=$u&t=$token&s=$salt&v=1.16.1&c=Sillon"
    }

    private fun coverUrl(coverArtId: String): String =
        "$base/rest/getCoverArt?id=${URLEncoder.encode(coverArtId, "UTF-8")}&${authQuery()}&size=400"

    private fun streamUrl(songId: String): String =
        "$base/rest/stream?id=${URLEncoder.encode(songId, "UTF-8")}&${authQuery()}"

    override suspend fun recentAlbums(limit: Int): List<Album> =
        api("getAlbumList2", mapOf("type" to "newest", "size" to limit.toString()))
            .albumList2?.album.orEmpty().map(::toAlbum)

    override suspend fun searchAlbums(query: String): List<Album> =
        api("search3", mapOf("query" to query, "albumCount" to "50", "songCount" to "0", "artistCount" to "0"))
            .searchResult3?.album.orEmpty().map(::toAlbum)

    override suspend fun albumsByArtistName(name: String): List<Album> {
        val artists = api("search3", mapOf("query" to name, "artistCount" to "5", "albumCount" to "0", "songCount" to "0"))
            .searchResult3?.artist.orEmpty()
        val artist = artists.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: artists.firstOrNull() ?: return emptyList()
        return api("getArtist", mapOf("id" to artist.id)).artist?.album.orEmpty().map(::toAlbum)
    }

    override suspend fun tracks(albumId: String): List<Track> =
        api("getAlbum", mapOf("id" to albumId)).album?.song.orEmpty().map { s ->
            Track(
                id = s.id,
                title = s.title,
                artist = s.artist,
                index = s.track,
                durationMs = s.duration?.let { it * 1000L },
                streamUrl = streamUrl(s.id),
                coverUrl = coverUrl(s.coverArt ?: s.id),
                serverId = config.id,
            )
        }

    override suspend fun lyrics(trackId: String): TrackLyrics? {
        val sl = runCatching { api("getLyricsBySongId", mapOf("id" to trackId)).lyricsList?.structuredLyrics?.firstOrNull() }
            .getOrNull() ?: return null
        val lines = sl.line.map { LyricLine(it.value, if (sl.synced) it.start?.let { ms -> ms / 1000.0 } else null) }
        if (lines.isEmpty()) return null
        return TrackLyrics(synced = sl.synced && lines.any { it.timeSeconds != null }, lines = lines)
    }

    override fun close() = http.close()

    private fun toAlbum(a: SubAlbumItem) =
        Album(a.id, a.name, a.artist, coverUrl(a.coverArt ?: a.id), config.id)

    companion object {
        private fun generateSalt(): String {
            val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
            return bytes.joinToString("") { "%02x".format(it) }
        }

        private fun md5(s: String): String =
            MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Vérifie les identifiants (ping) et renvoie la config. */
        suspend fun authenticate(id: String, url: String, username: String, password: String): ServerConfig {
            val config = ServerConfig(
                id = id,
                type = ServerType.SUBSONIC,
                name = "$username · Navidrome",
                baseUrl = url.trim().trimEnd('/'),
                username = username,
                password = password,
            )
            val p = SubsonicProvider(config)
            try {
                val resp = p.api("ping")
                if (resp.status != "ok") throw IllegalStateException("Identifiants refusés par le serveur")
                return config
            } finally {
                p.close()
            }
        }
    }
}
