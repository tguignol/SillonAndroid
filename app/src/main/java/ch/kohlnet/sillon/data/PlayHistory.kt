package ch.kohlnet.sillon.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Statistique d'écoute d'une piste : instantané (pour l'affichage + la relecture) + compteur d'écoutes
 * et date de dernière écoute (epoch ms). Sert aux sections d'accueil « les plus écoutés / récemment écoutés ».
 */
@Serializable
data class PlayStat(
    val trackId: String,
    val serverId: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String? = null,
    val streamUrl: String,
    val durationMs: Long? = null,
    val index: Int? = null,
    val count: Int = 0,
    val lastPlayedAt: Long = 0L,
) {
    /** Clé piste (titre+artiste normalisés) — fusionne une même chanson entre serveurs. */
    fun matchKey(): String = (title + " " + artist).trim().lowercase().replace(Regex("\\s+"), " ")

    /** Clé album (album+artiste normalisés) → relier la piste à son album dans la bibliothèque. */
    fun albumKey(): String = (album + " " + artist).trim().lowercase().replace(Regex("\\s+"), " ")

    fun toTrack(): Track = Track(
        id = trackId, title = title, artist = artist, index = index, durationMs = durationMs,
        streamUrl = streamUrl, coverUrl = coverUrl, serverId = serverId, album = album.ifBlank { null },
    )
}

/**
 * Historique d'écoute (singleton, persisté). Chaque démarrage de piste incrémente son compteur et
 * met à jour sa dernière écoute. Lecture seule côté serveurs ; tout est local.
 */
object PlayHistory {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val KEY = stringPreferencesKey("playHistory")
    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX = 400 // borne la taille persistée (on garde les plus récents)
    private var appContext: Context? = null

    private val _stats = MutableStateFlow<List<PlayStat>>(emptyList())
    val stats: StateFlow<List<PlayStat>> = _stats.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            val raw = context.applicationContext.dataStore.data.first()[KEY]
            _stats.value = raw?.let { runCatching { json.decodeFromString<List<PlayStat>>(it) }.getOrNull() } ?: emptyList()
        }
    }

    /** Enregistre le démarrage d'écoute de `track` : +1 au compteur, dernière écoute = maintenant. */
    fun record(track: Track) {
        if (track.title.isBlank()) return
        val now = System.currentTimeMillis()
        val key = track.matchKey()
        val list = _stats.value.toMutableList()
        val i = list.indexOfFirst { it.matchKey() == key }
        if (i >= 0) {
            val s = list[i]
            list[i] = s.copy(
                trackId = track.id,
                serverId = track.serverId,
                album = track.album ?: s.album,
                coverUrl = track.coverUrl ?: s.coverUrl,
                streamUrl = track.streamUrl,
                durationMs = track.durationMs ?: s.durationMs,
                index = track.index ?: s.index,
                count = s.count + 1,
                lastPlayedAt = now,
            )
        } else {
            list.add(
                PlayStat(
                    trackId = track.id, serverId = track.serverId, title = track.title, artist = track.artist,
                    album = track.album ?: "", coverUrl = track.coverUrl, streamUrl = track.streamUrl,
                    durationMs = track.durationMs, index = track.index, count = 1, lastPlayedAt = now,
                )
            )
        }
        val trimmed = if (list.size > MAX) list.sortedByDescending { it.lastPlayedAt }.take(MAX) else list
        _stats.value = trimmed
        persist(trimmed)
    }

    private fun persist(list: List<PlayStat>) {
        val ctx = appContext ?: return
        val s = runCatching { json.encodeToString(list) }.getOrNull() ?: return
        scope.launch { ctx.dataStore.edit { it[KEY] = s } }
    }
}
