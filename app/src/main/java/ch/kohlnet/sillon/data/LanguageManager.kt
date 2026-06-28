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
import java.util.Locale

/**
 * Langues proposées — miroir exact de `AppLanguage` (iOS `LanguageManager.swift`) :
 * « Automatique » + les 10 langues les plus parlées en Suisse (4 nationales + immigration/international).
 * `code` = code ISO ou `null` pour suivre la langue du système. `displayName` = endonyme.
 */
enum class AppLanguage(val code: String?, val displayName: String) {
    SYSTEM(null, "Automatique (langue du système)"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    IT("it", "Italiano"),
    EN("en", "English"),
    PT("pt", "Português"),
    SQ("sq", "Shqip"),
    ES("es", "Español"),
    SR("sr", "Srpski"),
    RM("rm", "Rumantsch"),
    TR("tr", "Türkçe"),
}

/**
 * Gère la langue de l'interface. Bascule À CHAUD (sans redémarrage) : l'UI lit le `StateFlow`
 * [current] via `str(...)` et se recompose au changement — équivalent du `LocalizedBundle` iOS.
 * Persistance via le DataStore partagé (clé `appLanguage`, comme iOS `@AppStorage`).
 */
object LanguageManager {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val KEY = stringPreferencesKey("appLanguage")
    private var appContext: Context? = null

    private val _current = MutableStateFlow(AppLanguage.SYSTEM)
    val current: StateFlow<AppLanguage> = _current.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            val raw = context.applicationContext.dataStore.data.first()[KEY]
            _current.value = AppLanguage.entries.firstOrNull { it.name == raw } ?: AppLanguage.SYSTEM
        }
    }

    fun setLanguage(lang: AppLanguage) {
        _current.value = lang
        appContext?.let { ctx -> scope.launch { ctx.dataStore.edit { it[KEY] = lang.name } } }
    }

    /** Code de langue effectif : choix explicite, ou langue du système si « Automatique ». */
    fun resolvedCode(lang: AppLanguage = _current.value): String =
        lang.code ?: Locale.getDefault().language.lowercase()
}
