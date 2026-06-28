package ch.kohlnet.sillon.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Cache LOCAL des playlists SERVEUR (par serveur) → elles restent affichées même quand le serveur est
 * momentanément injoignable, et se rafraîchissent quand il revient. Métadonnées seulement (lecture
 * seule) : la lecture d'une playlist serveur va toujours chercher ses titres directement sur le serveur.
 * Fichier JSON de `filesDir` (écriture atomique, comme [LibraryCache]).
 */
object ServerPlaylistsCache {
    private val json = Json { ignoreUnknownKeys = true }
    private fun file(context: Context) = File(context.filesDir, "server-playlists.json")

    @Serializable
    private data class Entry(val serverId: String, val playlists: List<ServerPlaylist>)

    suspend fun load(context: Context): Map<String, List<ServerPlaylist>> = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) return@withContext emptyMap()
        runCatching {
            json.decodeFromString<List<Entry>>(f.readText()).associate { it.serverId to it.playlists }
        }.getOrDefault(emptyMap())
    }

    suspend fun save(context: Context, byServer: Map<String, List<ServerPlaylist>>) = withContext(Dispatchers.IO) {
        runCatching {
            val entries = byServer.map { (id, pls) -> Entry(id, pls) }
            val dst = file(context)
            val tmp = File(dst.parentFile, dst.name + ".tmp")
            tmp.writeText(json.encodeToString(entries))
            if (!tmp.renameTo(dst)) { dst.writeText(tmp.readText()); tmp.delete() }
        }
        Unit
    }
}
