package ch.kohlnet.sillon.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Cache LOCAL des métadonnées de bibliothèque (albums BRUTS par serveur), pour un affichage INSTANTANÉ
 * à l'ouverture de l'app : on montre tout de suite la dernière biblio connue, puis on rafraîchit depuis
 * les serveurs EN FOND. Aucune donnée serveur n'est modifiée (règle lecture seule) ; la lecture se
 * branche TOUJOURS directement au serveur (les `coverUrl`/`streamUrl` restent celles du serveur).
 *
 * Persisté dans un fichier JSON de `filesDir` (la biblio peut peser plusieurs Mo → hors DataStore
 * Preferences, qui réécrit tout son contenu à chaque édition).
 */
object LibraryCache {
    private val json = Json { ignoreUnknownKeys = true }
    private fun file(context: Context) = File(context.filesDir, "library-cache.json")

    @Serializable
    private data class Entry(val serverId: String, val albums: List<Album>)

    /** Albums en cache par serverId (vide si aucun cache / lecture échouée). */
    suspend fun load(context: Context): Map<String, List<Album>> = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) return@withContext emptyMap()
        runCatching {
            json.decodeFromString<List<Entry>>(f.readText()).associate { it.serverId to it.albums }
        }.getOrDefault(emptyMap())
    }

    /** Écrit l'instantané du cache (atomique : fichier temporaire puis renommage). */
    suspend fun save(context: Context, byServer: Map<String, List<Album>>) = withContext(Dispatchers.IO) {
        runCatching {
            val entries = byServer.map { (id, albums) -> Entry(id, albums) }
            val dst = file(context)
            val tmp = File(dst.parentFile, dst.name + ".tmp")
            tmp.writeText(json.encodeToString(entries))
            if (!tmp.renameTo(dst)) { dst.writeText(tmp.readText()); tmp.delete() }
        }
        Unit
    }
}
