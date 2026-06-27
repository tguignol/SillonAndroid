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
)

@Serializable
data class JellyfinItem(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("AlbumArtist") val albumArtist: String? = null,
)

@Serializable
data class JellyfinTrack(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("IndexNumber") val index: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("Artists") val artists: List<String>? = null,
)

@Serializable
data class TracksResponse(
    @SerialName("Items") val items: List<JellyfinTrack> = emptyList(),
)

/**
 * Client Jellyfin minimal (Ktor + OkHttp). Reproduit l'auth iOS : en-tête `X-Emby-Authorization`
 * (+ `Token` une fois authentifié). Lecture seule.
 */
class JellyfinClient(baseUrl: String) {
    /** URL de base normalisée (sans `/` final). */
    val base: String = baseUrl.trim().trimEnd('/')

    private val http = HttpClient(OkHttp) {
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

    /** Albums de la bibliothèque (les plus récents d'abord). */
    suspend fun albums(token: String, userId: String, limit: Int = 60): List<JellyfinItem> =
        http.get("$base/Items") {
            header("X-Emby-Authorization", authHeader(token))
            parameter("userId", userId)
            parameter("IncludeItemTypes", "MusicAlbum")
            parameter("Recursive", "true")
            parameter("SortBy", "DateCreated,SortName")
            parameter("SortOrder", "Descending")
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
            parameter("Fields", "Artists")
        }.body<TracksResponse>().items

    /** URL chargeable de la pochette (le jeton sert d'`api_key`). */
    fun coverUrl(itemId: String, token: String, maxWidth: Int = 400): String =
        "$base/Items/$itemId/Images/Primary?maxWidth=$maxWidth&api_key=$token"

    /** URL de flux audio (fichier d'origine, façon « direct play » ; ExoPlayer gère FLAC/MP3/AAC…). */
    fun streamUrl(itemId: String, token: String): String =
        "$base/Audio/$itemId/stream?static=true&api_key=$token"

    fun close() = http.close()
}
