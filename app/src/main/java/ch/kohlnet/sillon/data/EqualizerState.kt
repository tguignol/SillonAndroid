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

/** Modes d'égaliseur (mêmes que l'iOS) : sliders simples / sliders paramétriques / courbe graphique. */
enum class EQMode { NORMAL, PARAMETRIC, GRAPHIC }

/**
 * État GLOBAL de l'égaliseur (singleton, partagé entre l'UI et le [EqAudioProcessor] — même process).
 * Trois modes (Normal/Paramétrique/Graphique), nombre de bandes RÉGLABLE (6 à 12), fréquences log
 * 32 Hz → 16 kHz par défaut mais éditables 20 Hz–20 kHz, gain −12..+12 dB, largeur 0.05–5 oct
 * (filtres peaking). `generation` est incrémenté à chaque changement → le processeur recalcule.
 */
object EqualizerState {
    const val MIN_GAIN = -12f
    const val MAX_GAIN = 12f
    const val MIN_BANDS = 6
    const val MAX_BANDS = 12
    const val DEFAULT_BANDS = 8
    const val MIN_FREQ = 20f
    const val MAX_FREQ = 20_000f
    const val MIN_BW = 0.05f      // largeur en OCTAVES
    const val MAX_BW = 5.0f
    const val DEFAULT_BW = 1.0f

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val KEY_GAINS = stringPreferencesKey("eqGains")
    private val KEY_ENABLED = stringPreferencesKey("eqEnabled")
    private val KEY_BANDS = stringPreferencesKey("eqBands")
    private val KEY_FREQS = stringPreferencesKey("eqFreqs")
    private val KEY_BWS = stringPreferencesKey("eqBws")
    private val KEY_MODE = stringPreferencesKey("eqMode")
    private var appContext: Context? = null

    private val _bandCount = MutableStateFlow(DEFAULT_BANDS)
    val bandCount: StateFlow<Int> = _bandCount.asStateFlow()

    private val _frequencies = MutableStateFlow(computeFreqs(DEFAULT_BANDS))
    val frequencies: StateFlow<FloatArray> = _frequencies.asStateFlow()

    private val _bandwidths = MutableStateFlow(FloatArray(DEFAULT_BANDS) { DEFAULT_BW })
    val bandwidths: StateFlow<FloatArray> = _bandwidths.asStateFlow()

    private val _gains = MutableStateFlow(FloatArray(DEFAULT_BANDS) { 0f })
    val gains: StateFlow<FloatArray> = _gains.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _mode = MutableStateFlow(EQMode.NORMAL)
    val mode: StateFlow<EQMode> = _mode.asStateFlow()

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
            val g = prefs[KEY_GAINS]?.split(",")?.mapNotNull { it.toFloatOrNull() }
            _gains.value = if (g != null && g.size == n) g.toFloatArray() else FloatArray(n) { 0f }
            val f = prefs[KEY_FREQS]?.split(",")?.mapNotNull { it.toFloatOrNull() }
            _frequencies.value = if (f != null && f.size == n) f.toFloatArray() else computeFreqs(n)
            val bw = prefs[KEY_BWS]?.split(",")?.mapNotNull { it.toFloatOrNull() }
            _bandwidths.value = if (bw != null && bw.size == n) bw.toFloatArray() else FloatArray(n) { DEFAULT_BW }
            _enabled.value = prefs[KEY_ENABLED] == "1"
            _mode.value = prefs[KEY_MODE]?.let { runCatching { EQMode.valueOf(it) }.getOrNull() } ?: EQMode.NORMAL
            generation++
        }
    }

    fun setGain(band: Int, db: Float) {
        if (band !in _gains.value.indices) return
        _gains.value = _gains.value.copyOf().also { it[band] = db.coerceIn(MIN_GAIN, MAX_GAIN) }
        generation++
        persist()
    }

    fun setFrequency(band: Int, hz: Float) {
        if (band !in _frequencies.value.indices) return
        _frequencies.value = _frequencies.value.copyOf().also { it[band] = hz.coerceIn(MIN_FREQ, MAX_FREQ) }
        generation++
        persist()
    }

    fun setBandwidth(band: Int, octaves: Float) {
        if (band !in _bandwidths.value.indices) return
        _bandwidths.value = _bandwidths.value.copyOf().also { it[band] = octaves.coerceIn(MIN_BW, MAX_BW) }
        generation++
        persist()
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        generation++
        persist()
    }

    /** Change de mode. En NORMAL on ré-impose les fréquences log par défaut + largeur 1 oct (gains gardés), comme l'iOS. */
    fun setMode(m: EQMode) {
        if (m == _mode.value) return
        _mode.value = m
        if (m == EQMode.NORMAL) {
            _frequencies.value = computeFreqs(_bandCount.value)
            _bandwidths.value = FloatArray(_bandCount.value) { DEFAULT_BW }
        }
        generation++
        persist()
    }

    /** Change le nombre de bandes (6–12) ; recalcule fréquences/largeurs par défaut et remet les gains à plat. */
    fun setBandCount(n: Int) {
        val c = n.coerceIn(MIN_BANDS, MAX_BANDS)
        if (c == _bandCount.value) return
        _bandCount.value = c
        _frequencies.value = computeFreqs(c)
        _bandwidths.value = FloatArray(c) { DEFAULT_BW }
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
        val freqsStr = _frequencies.value.joinToString(",")
        val bwsStr = _bandwidths.value.joinToString(",")
        val en = if (_enabled.value) "1" else "0"
        val n = _bandCount.value.toString()
        val modeStr = _mode.value.name
        scope.launch {
            ctx.dataStore.edit {
                it[KEY_GAINS] = gainsStr
                it[KEY_FREQS] = freqsStr
                it[KEY_BWS] = bwsStr
                it[KEY_ENABLED] = en
                it[KEY_BANDS] = n
                it[KEY_MODE] = modeStr
            }
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
