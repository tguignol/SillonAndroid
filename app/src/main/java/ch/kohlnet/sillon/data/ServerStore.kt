package ch.kohlnet.sillon.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

internal val Context.dataStore by preferencesDataStore(name = "sillon")

/**
 * Persistance de la LISTE des serveurs (DataStore, JSON). Multi-serveur. On stocke le jeton Jellyfin
 * ou le mot de passe Subsonic — JAMAIS sur le réseau public ; à durcir (chiffrement façon Keychain).
 */
object ServerStore {
    private val KEY_SERVERS = stringPreferencesKey("servers")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(context: Context): List<ServerConfig> {
        val raw = context.dataStore.data.first()[KEY_SERVERS] ?: return emptyList()
        return runCatching { json.decodeFromString<List<ServerConfig>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun save(context: Context, servers: List<ServerConfig>) {
        context.dataStore.edit { it[KEY_SERVERS] = json.encodeToString(servers) }
    }
}
