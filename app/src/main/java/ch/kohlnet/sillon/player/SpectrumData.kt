package ch.kohlnet.sillon.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * Analyse FFT TEMPS RÉEL du PCM réellement joué (tapé dans [EqAudioProcessor], donc APRÈS l'égaliseur,
 * comme l'iOS). Publie des magnitudes par bande (0..1) + une forme d'onde, pour piloter un visualiseur
 * qui RÉAGIT AU SON (et ne « tourne plus bêtement en rond »). Mêmes constantes que l'iOS
 * `AudioSpectrumAnalyzer` : 48 bandes, FFT 1024, fenêtre de Hann, bandes log (bins 1→512),
 * dB = 20·log10(avg) ; attaque immédiate + chute 0.8/0.2.
 */
object SpectrumData {
    const val BANDS = 48
    private const val N = 1024            // taille de fenêtre FFT (puissance de 2)
    private const val HALF = N / 2        // 512 bins utiles
    const val WAVE = 128                  // points de forme d'onde (oscilloscope)

    private val _bands = MutableStateFlow(FloatArray(BANDS))
    val bands: StateFlow<FloatArray> = _bands.asStateFlow()

    private val _waveform = MutableStateFlow(FloatArray(WAVE))
    val waveform: StateFlow<FloatArray> = _waveform.asStateFlow()

    // Fenêtre + tampons de travail (réutilisés → pas d'alloc sur le thread audio).
    private val window = FloatArray(N)
    private var idx = 0
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val hann = FloatArray(N) { (0.5 - 0.5 * cos(2.0 * PI * it / (N - 1))).toFloat() }
    private val smooth = FloatArray(BANDS)

    // Bornes de bins par bande, réparties GÉOMÉTRIQUEMENT entre le bin 1 et 512 (comme l'iOS).
    private val bandLo = IntArray(BANDS)
    private val bandHi = IntArray(BANDS)

    init {
        for (b in 0 until BANDS) {
            val lo = HALF.toDouble().pow(b.toDouble() / BANDS).toInt().coerceIn(1, HALF - 1)
            var hi = HALF.toDouble().pow((b + 1.0) / BANDS).toInt()
            hi = hi.coerceIn(lo + 1, HALF)
            bandLo[b] = lo
            bandHi[b] = hi
        }
    }

    /** Empile un échantillon mono (-1..1) ; déclenche l'analyse quand la fenêtre est pleine. */
    fun feed(sample: Float) {
        window[idx++] = sample
        if (idx >= N) {
            idx = 0
            analyze()
        }
    }

    private fun analyze() {
        // Forme d'onde (oscilloscope) : sous-échantillonnage brut de la fenêtre.
        val wave = FloatArray(WAVE)
        val stride = N / WAVE
        for (i in 0 until WAVE) wave[i] = window[i * stride].coerceIn(-1f, 1f)
        _waveform.value = wave

        // Fenêtrage de Hann + FFT.
        for (i in 0 until N) {
            re[i] = window[i] * hann[i]
            im[i] = 0f
        }
        fft(re, im)

        val out = FloatArray(BANDS)
        for (b in 0 until BANDS) {
            var sum = 0f
            var cnt = 0
            for (k in bandLo[b] until bandHi[b]) {
                // Amplitude normalisée ~dBFS : gain cohérent de Hann = 0.5 → ×4/N.
                sum += hypot(re[k], im[k]) * 4f / N
                cnt++
            }
            val avg = if (cnt > 0) sum / cnt else 0f
            val db = 20f * log10(avg + 1e-7f)
            // Fenêtre -58 dBFS → -10 dBFS → 0..1 (évite que l'anneau sature et fige).
            out[b] = ((db + 58f) / 48f).coerceIn(0f, 1f)
        }

        // Lissage VU-mètre : attaque immédiate, chute exponentielle 0.8/0.2 (comme l'iOS).
        for (b in 0 until BANDS) {
            smooth[b] = if (out[b] > smooth[b]) out[b] else smooth[b] * 0.8f + out[b] * 0.2f
        }
        _bands.value = smooth.copyOf()
    }

    /** Remet le spectre à plat (changement de piste, seek, arrêt). */
    fun reset() {
        idx = 0
        for (b in 0 until BANDS) smooth[b] = 0f
        _bands.value = FloatArray(BANDS)
        _waveform.value = FloatArray(WAVE)
    }

    /** FFT radix-2 itérative en place (Cooley-Tukey). */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curR = 1f
                var curI = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val aR = re[i + k]
                    val aI = im[i + k]
                    val bR = re[i + k + half]
                    val bI = im[i + k + half]
                    val tR = bR * curR - bI * curI
                    val tI = bR * curI + bI * curR
                    re[i + k] = aR + tR
                    im[i + k] = aI + tI
                    re[i + k + half] = aR - tR
                    im[i + k + half] = aI - tI
                    val ncurR = curR * wr - curI * wi
                    curI = curR * wi + curI * wr
                    curR = ncurR
                }
                i += len
            }
            len = len shl 1
        }
    }
}
