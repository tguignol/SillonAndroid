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
 * Nombre de bandes RÉGLABLE (6 à 12, comme l'iOS), fréquences log 32 Hz → 16 kHz, gain −12..+12 dB,
 * filtres peaking 1 octave. `generation` est incrémenté à chaque changement → le processeur recalcule.
 */
object EqualizerState {
    const val MIN_GAIN = -12f
    const val MAX_GAIN = 12f
    const val MIN_BANDS = 6
    const val MAX_BANDS = 12
    const val DEFAULT_BANDS = 8

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val KEY_GAINS = stringPreferencesKey("eqGains")
    private val KEY_ENABLED = stringPreferencesKey("eqEnabled")
    private val KEY_BANDS = stringPreferencesKey("eqBands")
    private var appContext: Context? = null

    private val _bandCount = MutableStateFlow(DEFAULT_BANDS)
    val bandCount: StateFlow<Int> = _bandCount.asStateFlow()

    private val _frequencies = MutableStateFlow(computeFreqs(DEFAULT_BANDS))
    val frequencies: StateFlow<FloatArray> = _frequencies.asStateFlow()

    private val _gains = MutableStateFlow(FloatArray(DEFAULT_BANDS) { 0f })
    val gains: StateFlow<FloatArray> = _gains.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Incrémenté à chaque changement ; lu (sans verrou) par le processeur audio pour recalculer. */
    @Volatile
    var generation: Int = 0
        private set

    /** Fréquences centrales (Hz), réparties logarithmiquement entre 32 Hz et 16 kHz (formule iOS). */
    private fun computeFreqs(n: Int): FloatArray {
        val low = kotlin.math.ln(32.0) / kotlin.math.ln(2.0)
        val high = kotlin.math.ln(16_000.0) / kotlin.math.ln(2.0)
        val step = (high - low) / (n - 1)
        return FloatArray(n) { i -> Math.pow(2.0, low + step * i).toFloat() }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            val prefs = context.applicationContext.dataStore.data.first()
            val n = prefs[KEY_BANDS]?.toIntOrNull()?.coerceIn(MIN_BANDS, MAX_BANDS) ?: DEFAULT_BANDS
            _bandCount.value = n
            _frequencies.value = computeFreqs(n)
            val g = prefs[KEY_GAINS]?.split(",")?.mapNotNull { it.toFloatOrNull() }
            _gains.value = if (g != null && g.size == n) g.toFloatArray() else FloatArray(n) { 0f }
            _enabled.value = prefs[KEY_ENABLED] == "1"
            generation++
        }
    }

    fun setGain(band: Int, db: Float) {
        if (band !in _gains.value.indices) return
        _gains.value = _gains.value.copyOf().also { it[band] = db.coerceIn(MIN_GAIN, MAX_GAIN) }
        generation++
        persist()
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        generation++
        persist()
    }

    /** Change le nombre de bandes (6–12) ; recalcule les fréquences et remet les gains à plat. */
    fun setBandCount(n: Int) {
        val c = n.coerceIn(MIN_BANDS, MAX_BANDS)
        if (c == _bandCount.value) return
        _bandCount.value = c
        _frequencies.value = computeFreqs(c)
        _gains.value = FloatArray(c) { 0f }
        generation++
        persist()
    }

    fun reset() {
        _gains.value = FloatArray(_bandCount.value) { 0f }
        generation++
        persist()
    }

    private fun persist() {
        val ctx = appContext ?: return
        val gainsStr = _gains.value.joinToString(",")
        val en = if (_enabled.value) "1" else "0"
        val n = _bandCount.value.toString()
        scope.launch {
            ctx.dataStore.edit { it[KEY_GAINS] = gainsStr; it[KEY_ENABLED] = en; it[KEY_BANDS] = n }
        }
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
