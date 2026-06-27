package ch.kohlnet.sillon.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Favoris stockés EN LOCAL (DataStore, liste d'albums sérialisée JSON). On ne touche jamais au
 * serveur (cf. règle lecture seule). Réutilise le même DataStore que [ServerStore].
 */
object FavoritesStore {
    private val KEY = stringPreferencesKey("favorite_albums")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(context: Context): List<Album> {
        val raw = context.dataStore.data.first()[KEY] ?: return emptyList()
        return runCatching { json.decodeFromString<List<Album>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun save(context: Context, albums: List<Album>) {
        context.dataStore.edit { it[KEY] = json.encodeToString(albums) }
    }
}
