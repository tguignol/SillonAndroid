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

/** Styles de visualiseur, mêmes que l'iOS (`SpectrumRingView`). OFF = pochette ronde, OFF_SQUARE = carrée. */
enum class SpectrumStyle { CIRCULAR_BARS, BARS, WAVEFORM, CASCADE, OSCILLOSCOPE, OFF, OFF_SQUARE }

/** Style de spectre choisi dans le lecteur (persisté). Taper la pochette fait défiler les styles. */
object SpectrumPrefs {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val KEY = stringPreferencesKey("spectrumStyle")
    private var appContext: Context? = null

    private val _style = MutableStateFlow(SpectrumStyle.CIRCULAR_BARS)
    val style: StateFlow<SpectrumStyle> = _style.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            val raw = context.applicationContext.dataStore.data.first()[KEY]
            _style.value = SpectrumStyle.entries.firstOrNull { it.name == raw } ?: SpectrumStyle.CIRCULAR_BARS
        }
    }

    fun setStyle(s: SpectrumStyle) {
        _style.value = s
        appContext?.let { ctx -> scope.launch { ctx.dataStore.edit { it[KEY] = s.name } } }
    }

    /** Passe au style suivant (cycle). */
    fun cycle() {
        val entries = SpectrumStyle.entries
        setStyle(entries[(entries.indexOf(_style.value) + 1) % entries.size])
    }
}
