package ch.kohlnet.sillon.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.UUID

internal val Context.dataStore by preferencesDataStore(name = "sillon")

/**
 * Persistance de la LISTE des serveurs (DataStore, JSON). Multi-serveur. On stocke le jeton Jellyfin
 * ou le mot de passe Subsonic — JAMAIS sur le réseau public ; à durcir (chiffrement façon Keychain).
 */
object ServerStore {
    private val KEY_SERVERS = stringPreferencesKey("servers")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(context: Context): List<ServerConfig> {
        val prefs = context.dataStore.data.first()
        prefs[KEY_SERVERS]?.let { raw ->
            return runCatching { json.decodeFromString<List<ServerConfig>>(raw) }.getOrDefault(emptyList())
        }
        // Migration de l'ancien format mono-serveur (clés baseUrl/token/userId/username) → liste.
        val baseUrl = prefs[stringPreferencesKey("baseUrl")]
        val token = prefs[stringPreferencesKey("token")]
        val userId = prefs[stringPreferencesKey("userId")]
        if (baseUrl != null && token != null && userId != null) {
            val username = prefs[stringPreferencesKey("username")].orEmpty()
            val migrated = listOf(
                ServerConfig(
                    id = UUID.randomUUID().toString(),
                    type = ServerType.JELLYFIN,
                    name = "${username.ifBlank { "Jellyfin" }} · Jellyfin",
                    baseUrl = baseUrl,
                    username = username,
                    token = token,
                    userId = userId,
                )
            )
            save(context, migrated)
            return migrated
        }
        return emptyList()
    }

    suspend fun save(context: Context, servers: List<ServerConfig>) {
        context.dataStore.edit { it[KEY_SERVERS] = json.encodeToString(servers) }
    }
}
