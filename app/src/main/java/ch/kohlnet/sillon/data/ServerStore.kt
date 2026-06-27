package ch.kohlnet.sillon.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Config serveur persistée (sans mot de passe — on garde le JETON, comme le Keychain iOS). */
data class SavedServer(
    val baseUrl: String,
    val userId: String,
    val token: String,
    val username: String,
)

private val Context.dataStore by preferencesDataStore(name = "sillon")

/**
 * Persistance légère de la connexion (DataStore). On stocke l'URL, l'identifiant utilisateur, le
 * **jeton d'accès** et le nom — JAMAIS le mot de passe. (À durcir plus tard : chiffrement façon Keychain.)
 */
object ServerStore {
    private val KEY_URL = stringPreferencesKey("baseUrl")
    private val KEY_USER_ID = stringPreferencesKey("userId")
    private val KEY_TOKEN = stringPreferencesKey("token")
    private val KEY_USERNAME = stringPreferencesKey("username")

    suspend fun save(context: Context, server: SavedServer) {
        context.dataStore.edit { prefs ->
            prefs[KEY_URL] = server.baseUrl
            prefs[KEY_USER_ID] = server.userId
            prefs[KEY_TOKEN] = server.token
            prefs[KEY_USERNAME] = server.username
        }
    }

    suspend fun load(context: Context): SavedServer? {
        val prefs = context.dataStore.data.first()
        val url = prefs[KEY_URL] ?: return null
        val userId = prefs[KEY_USER_ID] ?: return null
        val token = prefs[KEY_TOKEN] ?: return null
        val username = prefs[KEY_USERNAME] ?: return null
        return SavedServer(url, userId, token, username)
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { it.clear() }
    }
}
