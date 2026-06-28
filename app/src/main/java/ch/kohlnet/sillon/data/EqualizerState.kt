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
import kotlin.math.roundToInt

/**
 * État GLOBAL de l'égaliseur (singleton, partagé entre l'UI et le [EqAudioProcessor] — même process).
 * 8 bandes par défaut, fréquences log 32 Hz → 16 kHz (mêmes que l'iOS), gain −12..+12 dB, filtres
 * peaking 1 octave. `generation` est incrémenté à chaque changement → le processeur recalcule ses coeffs.
 */
object EqualizerState {
    const val MIN_GAIN = -12f
    const val MAX_GAIN = 12f
    const val BAND_COUNT = 8

    /** Fréquences centrales (Hz), réparties logarithmiquement entre 32 Hz et 16 kHz (formule iOS). */
    val frequencies: FloatArray = run {
        val low = kotlin.math.ln(32.0) / kotlin.math.ln(2.0)
        val high = kotlin.math.ln(16_000.0) / kotlin.math.ln(2.0)
        val step = (high - low) / (BAND_COUNT - 1)
        FloatArray(BAND_COUNT) { i -> Math.pow(2.0, low + step * i).toFloat() }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val KEY_GAINS = stringPreferencesKey("eqGains")
    private val KEY_ENABLED = stringPreferencesKey("eqEnabled")
    private var appContext: Context? = null

    private val _gains = MutableStateFlow(FloatArray(BAND_COUNT) { 0f })
    val gains: StateFlow<FloatArray> = _gains.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Incrémenté à chaque changement ; lu (sans verrou) par le processeur audio pour recalculer. */
    @Volatile
    var generation: Int = 0
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            val prefs = context.applicationContext.dataStore.data.first()
            prefs[KEY_GAINS]?.split(",")?.mapNotNull { it.toFloatOrNull() }?.let { g ->
                if (g.size == BAND_COUNT) _gains.value = g.toFloatArray()
            }
            _enabled.value = prefs[KEY_ENABLED] == "1"
            generation++
        }
    }

    fun setGain(band: Int, db: Float) {
        if (band !in 0 until BAND_COUNT) return
        _gains.value = _gains.value.copyOf().also { it[band] = db.coerceIn(MIN_GAIN, MAX_GAIN) }
        generation++
        persist()
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        generation++
        persist()
    }

    fun reset() {
        _gains.value = FloatArray(BAND_COUNT) { 0f }
        generation++
        persist()
    }

    private fun persist() {
        val ctx = appContext ?: return
        val gainsStr = _gains.value.joinToString(",")
        val en = if (_enabled.value) "1" else "0"
        scope.launch { ctx.dataStore.edit { it[KEY_GAINS] = gainsStr; it[KEY_ENABLED] = en } }
    }

    /** Libellé court d'une fréquence : « 32 », « 1.1k », « 16k ». */
    fun frequencyLabel(hz: Float): String = when {
        hz >= 1000f -> {
            val k = hz / 1000f
            if (k >= 10f) "${k.roundToInt()}k" else "${(k * 10).roundToInt() / 10.0}k".replace(".0k", "k")
        }
        else -> "${hz.roundToInt()}"
    }
}
