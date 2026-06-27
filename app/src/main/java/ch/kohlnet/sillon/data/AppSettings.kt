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

/** Apparence choisie dans les réglages (comme l'iOS). */
enum class AppearanceMode(val label: String) {
    SYSTEM("Système"),
    LIGHT("Clair"),
    DARK("Sombre"),
}

/** Réglages de l'app persistés (apparence…). Réutilise le DataStore partagé. */
object AppSettings {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val KEY_APPEARANCE = stringPreferencesKey("appearance")
    private var appContext: Context? = null

    private val _appearance = MutableStateFlow(AppearanceMode.SYSTEM)
    val appearance: StateFlow<AppearanceMode> = _appearance.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            val raw = context.applicationContext.dataStore.data.first()[KEY_APPEARANCE]
            _appearance.value = AppearanceMode.entries.firstOrNull { it.name == raw } ?: AppearanceMode.SYSTEM
        }
    }

    fun setAppearance(mode: AppearanceMode) {
        _appearance.value = mode
        appContext?.let { ctx -> scope.launch { ctx.dataStore.edit { it[KEY_APPEARANCE] = mode.name } } }
    }
}
