package ch.kohlnet.sillon.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Playlist LOCALE à l'app (jamais écrite sur les serveurs : règle lecture seule). On stocke des
 * INSTANTANÉS de pistes (`Track` est `@Serializable`) plutôt que des références : robuste au
 * multi-serveurs / à la dédup / au réordonnancement serveur, comme [PlayStat]. L'ORDRE de la liste
 * `tracks` EST l'ordre de lecture (pas besoin d'un champ `position` comme l'iOS SwiftData).
 */
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tracks: List<Track> = emptyList(),
)

/** Persistance des playlists dans un fichier JSON de `filesDir` (écriture atomique, comme [LibraryCache]). */
object PlaylistStore {
    private val json = Json { ignoreUnknownKeys = true }
    private fun file(context: Context) = File(context.filesDir, "playlists.json")

    suspend fun load(context: Context): List<Playlist> = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) return@withContext emptyList()
        runCatching { json.decodeFromString<List<Playlist>>(f.readText()) }.getOrDefault(emptyList())
    }

    suspend fun save(context: Context, playlists: List<Playlist>) = withContext(Dispatchers.IO) {
        runCatching {
            val dst = file(context)
            val tmp = File(dst.parentFile, dst.name + ".tmp")
            tmp.writeText(json.encodeToString(playlists))
            if (!tmp.renameTo(dst)) { dst.writeText(tmp.readText()); tmp.delete() }
        }
        Unit
    }
}

/**
 * Source de vérité des playlists locales : `StateFlow` observable + CRUD + réordonnancement. Centralisé
 * ici (façon [PlaylistActions] iOS) pour garder les vues déclaratives. Chaque mutation horodate
 * `updatedAt`, met à jour le flux et persiste.
 */
object Playlists {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    /** Playlists, les plus RÉCEMMENT MODIFIÉES d'abord (façon iOS `sort: updatedAt reverse`). */
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch { _playlists.value = sorted(PlaylistStore.load(context.applicationContext)) }
    }

    /** Crée une playlist (nom par défaut si vide) et la renvoie. */
    fun create(name: String): Playlist {
        val now = System.currentTimeMillis()
        val pl = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Nouvelle playlist" },
            createdAt = now,
            updatedAt = now,
        )
        _playlists.value = sorted(_playlists.value + pl)
        persist()
        return pl
    }

    fun rename(id: String, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        update(id) { it.copy(name = clean) }
    }

    fun delete(id: String) {
        _playlists.value = _playlists.value.filterNot { it.id == id }
        persist()
    }

    /** Ajoute des pistes à LA FIN (doublons autorisés : une playlist peut répéter un titre). */
    fun addTracks(id: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        update(id) { it.copy(tracks = it.tracks + tracks) }
    }

    /** Retire la piste à l'index donné (dans l'ordre de la playlist). */
    fun removeAt(id: String, index: Int) =
        update(id) { pl ->
            if (index !in pl.tracks.indices) pl
            else pl.copy(tracks = pl.tracks.toMutableList().also { it.removeAt(index) })
        }

    /** Déplace une piste de `from` vers `to` (réordonnancement général ; base du glisser-déposer). */
    fun move(id: String, from: Int, to: Int) =
        update(id) { pl ->
            val list = pl.tracks
            if (from !in list.indices || to !in list.indices || from == to) pl
            else pl.copy(tracks = list.toMutableList().also { it.add(to, it.removeAt(from)) })
        }

    /** Monte d'un cran la piste à `index` (flèche ↑). */
    fun moveUp(id: String, index: Int) { if (index > 0) move(id, index, index - 1) }

    /** Descend d'un cran la piste à `index` (flèche ↓). */
    fun moveDown(id: String, index: Int) {
        val pl = _playlists.value.firstOrNull { it.id == id } ?: return
        if (index < pl.tracks.lastIndex) move(id, index, index + 1)
    }

    /** Vue à jour d'une playlist (le détail observe le flux et relit par id). */
    fun byId(id: String): Playlist? = _playlists.value.firstOrNull { it.id == id }

    private fun update(id: String, transform: (Playlist) -> Playlist) {
        val now = System.currentTimeMillis()
        _playlists.value = sorted(
            _playlists.value.map { if (it.id == id) transform(it).copy(updatedAt = now) else it }
        )
        persist()
    }

    private fun sorted(list: List<Playlist>) = list.sortedByDescending { it.updatedAt }

    private fun persist() {
        val ctx = appContext ?: return
        val snapshot = _playlists.value
        scope.launch { PlaylistStore.save(ctx, snapshot) }
    }
}
