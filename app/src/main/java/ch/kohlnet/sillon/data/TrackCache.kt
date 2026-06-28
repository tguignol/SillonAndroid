package ch.kohlnet.sillon.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Cache LOCAL des PISTES par album (ouverture d'album instantanée au 2ᵉ accès). Clé = `serverId/albumId`
 * → server-aware (pas de collision entre serveurs). Borné en nombre d'albums (LRU sur la dernière
 * consultation) pour ne pas grossir sans limite ; la borne est volontairement GÉNÉREUSE afin d'encaisser
 * l'ajout de plusieurs serveurs. Comme pour [LibraryCache] : métadonnées seulement, le streaming va
 * toujours chercher le flux directement sur le serveur (règle lecture seule).
 *
 * Fichier JSON dans `filesDir` (peut peser quelques Mo → hors DataStore Preferences).
 */
object TrackCache {
    /** Nombre max d'albums dont on garde les pistes (≈ doublé pour laisser de la marge multi-serveurs). */
    const val MAX_ALBUMS = 1000

    private val json = Json { ignoreUnknownKeys = true }
    private fun file(context: Context) = File(context.filesDir, "track-cache.json")

    @Serializable
    data class Entry(val key: String, val tracks: List<Track>, val lastAccess: Long = 0L)

    /** Pistes en cache par clé (les plus récemment consultées d'abord, tronquées à [MAX_ALBUMS]). */
    suspend fun load(context: Context): List<Entry> = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<Entry>>(f.readText())
                .sortedByDescending { it.lastAccess }
                .take(MAX_ALBUMS)
        }.getOrDefault(emptyList())
    }

    /** Écrit l'instantané (tronqué LRU à [MAX_ALBUMS]) de façon atomique. */
    suspend fun save(context: Context, entries: List<Entry>) = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = entries.sortedByDescending { it.lastAccess }.take(MAX_ALBUMS)
            val dst = file(context)
            val tmp = File(dst.parentFile, dst.name + ".tmp")
            tmp.writeText(json.encodeToString(trimmed))
            if (!tmp.renameTo(dst)) { dst.writeText(tmp.readText()); tmp.delete() }
        }
        Unit
    }
}
