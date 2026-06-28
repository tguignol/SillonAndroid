package ch.kohlnet.sillon.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// MARK: - DTOs Jellyfin (JSON en PascalCase → @SerialName)

@Serializable
data class AuthRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String,
)

@Serializable
data class AuthResponse(
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("User") val user: JellyfinUser,
)

@Serializable
data class JellyfinUser(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
)

@Serializable
data class ItemsResponse(
    @SerialName("Items") val items: List<JellyfinItem> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int? = null,
)

@Serializable
data class JellyfinItem(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("AlbumArtist") val albumArtist: String? = null,
)

@Serializable
data class JellyfinMediaStream(
    @SerialName("Type") val type: String? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("BitRate") val bitRate: Int? = null,      // bits/s
    @SerialName("SampleRate") val sampleRate: Int? = null,
    @SerialName("BitDepth") val bitDepth: Int? = null,
)

@Serializable
data class JellyfinTrack(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("IndexNumber") val index: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("Artists") val artists: List<String>? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("MediaStreams") val mediaStreams: List<JellyfinMediaStream>? = null,
)

@Serializable
data class TracksResponse(
    @SerialName("Items") val items: List<JellyfinTrack> = emptyList(),
)

@Serializable
data class LyricLineDto(
    @SerialName("Text") val text: String = "",
    @SerialName("Start") val start: Long? = null, // ticks .NET (100 ns) ; nil = paroles non synchronisées
)

@Serializable
data class LyricsDto(
    @SerialName("Lyrics") val lyrics: List<LyricLineDto> = emptyList(),
)

/**
 * Client Jellyfin minimal (Ktor + OkHttp). Reproduit l'auth iOS : en-tête `X-Emby-Authorization`
 * (+ `Token` une fois authentifié). Lecture seule.
 */
class JellyfinClient(baseUrl: String) {
    /** URL de base normalisée (sans `/` final). */
    val base: String = baseUrl.trim().trimEnd('/')

    private val http = HttpClient(OkHttp) {
        engine {
            config {
                retryOnConnectionFailure(true)
                connectTimeout(java.time.Duration.ofSeconds(30))
                readTimeout(java.time.Duration.ofSeconds(60))   // grosses réponses /Items sur lien lent
                writeTimeout(java.time.Duration.ofSeconds(30))
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun authHeader(token: String? = null): String {
        val h = "MediaBrowser Client=\"Sillon\", Device=\"Android\", DeviceId=\"sillon-android\", Version=\"1.0\""
        return if (token != null) "$h, Token=\"$token\"" else h
    }

    /** Authentifie l'utilisateur ; renvoie le jeton d'accès + l'utilisateur. */
    suspend fun authenticate(username: String, password: String): AuthResponse =
        http.post("$base/Users/AuthenticateByName") {
            header("X-Emby-Authorization", authHeader())
            contentType(ContentType.Application.Json)
            setBody(AuthRequest(username, password))
        }.body()

    /** TOUS les albums de la bibliothèque (paginés jusqu'au bout), récents d'abord. */
    suspend fun allAlbums(token: String, userId: String): List<JellyfinItem> {
        val pageSize = 500
        var start = 0
        val out = mutableListOf<JellyfinItem>()
        while (true) {
            val resp = http.get("$base/Items") {
                header("X-Emby-Authorization", authHeader(token))
                parameter("userId", userId)
                parameter("IncludeItemTypes", "MusicAlbum")
                parameter("Recursive", "true")
                parameter("SortBy", "DateCreated,SortName")
                parameter("SortOrder", "Descending")
                parameter("StartIndex", start.toString())
                parameter("Limit", pageSize.toString())
                parameter("Fields", "AlbumArtist")
                parameter("EnableImages", "false")     // allège la réponse → chargement plus rapide
                parameter("EnableUserData", "false")
            }.body<ItemsResponse>()
            out += resp.items
            val total = resp.totalRecordCount
            if (resp.items.size < pageSize) break
            if (total != null && out.size >= total) break
            start += pageSize
            if (start > 1_000_000) break // garde-fou
        }
        return out
    }

    /** Recherche d'albums par terme. */
    suspend fun searchAlbums(token: String, userId: String, query: String, limit: Int = 50): List<JellyfinItem> =
        http.get("$base/Items") {
            header("X-Emby-Authorization", authHeader(token))
            parameter("userId", userId)
            parameter("searchTerm", query)
            parameter("IncludeItemTypes", "MusicAlbum")
            parameter("Recursive", "true")
            parameter("Limit", limit.toString())
            parameter("Fields", "AlbumArtist")
        }.body<ItemsResponse>().items

    /** Recherche d'artistes par terme. */
    suspend fun searchArtists(token: String, userId: String, query: String, limit: Int = 10): List<JellyfinItem> =
        http.get("$base/Artists") {
            header("X-Emby-Authorization", authHeader(token))
            parameter("userId", userId)
            parameter("searchTerm", query)
            parameter("Limit", limit.toString())
        }.body<ItemsResponse>().items

    /** Albums d'un artiste (par son id). */
    suspend fun albumsByArtist(token: String, userId: String, artistId: String, limit: Int = 200): List<JellyfinItem> =
        http.get("$base/Items") {
            header("X-Emby-Authorization", authHeader(token))
            parameter("userId", userId)
            parameter("albumArtistIds", artistId)
            parameter("IncludeItemTypes", "MusicAlbum")
            parameter("Recursive", "true")
            parameter("SortBy", "ProductionYear,SortName")
            parameter("Limit", limit.toString())
            parameter("Fields", "AlbumArtist")
        }.body<ItemsResponse>().items

    /** Morceaux d'un album, dans l'ordre des pistes. */
    suspend fun albumTracks(token: String, userId: String, albumId: String): List<JellyfinTrack> =
        http.get("$base/Items") {
            header("X-Emby-Authorization", authHeader(token))
            parameter("userId", userId)
            parameter("parentId", albumId)
            parameter("IncludeItemTypes", "Audio")
            parameter("SortBy", "ParentIndexNumber,IndexNumber,SortName")
            parameter("Fields", "Artists,MediaStreams,Path,Container")
        }.body<TracksResponse>().items

    /** URL chargeable de la pochette (le jeton sert d'`api_key`). */
    fun coverUrl(itemId: String, token: String, maxWidth: Int = 400): String =
        "$base/Items/$itemId/Images/Primary?maxWidth=$maxWidth&api_key=$token"

    /** URL de flux audio (fichier d'origine, façon « direct play » ; ExoPlayer gère FLAC/MP3/AAC…). */
    fun streamUrl(itemId: String, token: String): String =
        "$base/Audio/$itemId/stream?static=true&api_key=$token"

    /** Paroles d'un morceau (synchronisées ou simples), ou `null` si absentes. */
    suspend fun lyrics(token: String, itemId: String): LyricsDto? {
        val resp = http.get("$base/Audio/$itemId/Lyrics") {
            header("X-Emby-Authorization", authHeader(token))
        }
        if (!resp.status.isSuccess()) return null
        return runCatching { resp.body<LyricsDto>() }.getOrNull()
    }

    fun close() = http.close()
}
