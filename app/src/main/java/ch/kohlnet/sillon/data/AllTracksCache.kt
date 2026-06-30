package ch.kohlnet.sillon.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Cache LOCAL de TOUS les titres (onglet « Titres » de la Bibliothèque), pour un affichage INSTANTANÉ :
 * au lancement on montre tout de suite la dernière liste connue, puis on rafraîchit depuis les serveurs
 * EN FOND. Évite d'attendre le téléchargement (jusqu'à 5000 titres) à chaque ouverture de l'onglet.
 * Lecture seule côté serveur ; les `streamUrl`/`coverUrl` restent celles du serveur. Fichier JSON dans
 * `filesDir` (peut peser plusieurs Mo → hors DataStore).
 */
object AllTracksCache {
    private val json = Json { ignoreUnknownKeys = true }
    private fun file(context: Context) = File(context.filesDir, "all-tracks-cache.json")

    /** Titres en cache (vide si aucun cache / lecture échouée). */
    suspend fun load(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) return@withContext emptyList()
        runCatching { json.decodeFromString<List<Track>>(f.readText()) }.getOrDefault(emptyList())
    }

    /** Écrit l'instantané (atomique : fichier temporaire puis renommage). */
    suspend fun save(context: Context, tracks: List<Track>) = withContext(Dispatchers.IO) {
        runCatching {
            val dst = file(context)
            val tmp = File(dst.parentFile, dst.name + ".tmp")
            tmp.writeText(json.encodeToString(tracks))
            if (!tmp.renameTo(dst)) { dst.writeText(tmp.readText()); tmp.delete() }
        }
        Unit
    }
}
