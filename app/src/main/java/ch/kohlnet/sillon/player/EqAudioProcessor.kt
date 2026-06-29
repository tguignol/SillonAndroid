package ch.kohlnet.sillon.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import ch.kohlnet.sillon.data.EqualizerState
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tanh

/**
 * Égaliseur custom (chaîne de filtres peaking biquad) inséré dans la sortie ExoPlayer, pour reproduire
 * EXACTEMENT les bandes log de l'iOS (32 Hz → 16 kHz). Lit ses gains/on-off depuis [EqualizerState]
 * (même process) et recalcule ses coefficients quand `generation` change. PCM 16 bits.
 */
@UnstableApi
class EqAudioProcessor : BaseAudioProcessor() {
    private var sampleRate = 0
    private var channels = 0
    private var lastGen = -1
    private var bypass = true
    private var biquads: Array<Array<Biquad>> = arrayOf()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        lastGen = -1
        return inputAudioFormat
    }

    override fun onFlush() {
        SpectrumData.reset() // seek / changement de piste → repartir d'un spectre à plat
    }

    private fun recompute() {
        val gains = EqualizerState.gains.value
        val freqs = EqualizerState.frequencies.value
        val bws = EqualizerState.bandwidths.value
        val n = minOf(gains.size, freqs.size)
        bypass = !EqualizerState.enabled.value
        biquads = Array(channels) { _ ->
            Array(n) { b ->
                Biquad.peaking(freqs[b].toDouble(), gains[b].toDouble(), bws.getOrElse(b) { 1f }.toDouble(), sampleRate)
            }
        }
        lastGen = EqualizerState.generation
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (EqualizerState.generation != lastGen) recompute()
        feedSpectrum(inputBuffer) // analyse du son réellement joué (même si l'EQ est désactivé)
        // On copie l'entrée dans un tableau AVANT d'obtenir le buffer de sortie : le pipeline Media3
        // peut réutiliser NOTRE PROPRE buffer de sortie comme buffer d'entrée (alias). Une écriture
        // « en place » (`output.put(inputBuffer)` ou la boucle EQ avec source == destination) lèverait
        // alors `IllegalArgumentException: The source buffer is this buffer` → lecture impossible
        // (aucun `onPlayerError` → échec silencieux). Lire l'entrée d'abord garantit src ≠ dst.
        val order = inputBuffer.order()
        val inBytes = ByteArray(inputBuffer.remaining())
        inputBuffer.get(inBytes)
        val output = replaceOutputBuffer(inBytes.size)

        // Préampli : gain de SORTIE (dB → facteur linéaire), lu en direct. Appliqué que l'EQ soit on/off.
        val preampDb = EqualizerState.preampDb.value
        val gain = if (preampDb > 0f) Math.pow(10.0, preampDb / 20.0) else 1.0

        // Aucun traitement (EQ désactivé ET pas de préampli) → copie directe (rapide).
        if ((bypass || channels <= 0) && gain == 1.0) {
            output.put(inBytes)
            output.flip()
            return
        }
        val src = ByteBuffer.wrap(inBytes).order(order)
        var ch = 0
        while (src.hasRemaining()) {
            var y = src.short.toDouble()
            if (!bypass && channels > 0) for (bq in biquads[ch]) y = bq.process(y)
            if (gain != 1.0) {
                // Gain puis limiteur DOUX : transparent jusqu'à ~−2.5 dBFS, saturation douce au-delà
                // (au lieu d'un écrêtage dur) → plus fort sans distorsion agressive.
                y = softClip(y / 32768.0 * gain) * 32767.0
            }
            output.putShort(y.coerceIn(-32768.0, 32767.0).roundToInt().toShort())
            if (channels > 0) ch = (ch + 1) % channels
        }
        output.flip()
    }

    /** Limiteur doux : linéaire jusqu'au seuil `t`, puis saturation tanh, borné à ±1. */
    private fun softClip(v: Double): Double {
        val t = 0.85
        return when {
            v > t -> t + (1.0 - t) * tanh((v - t) / (1.0 - t))
            v < -t -> -(t + (1.0 - t) * tanh((-v - t) / (1.0 - t)))
            else -> v
        }
    }

    /** Lit le PCM (sans le consommer) pour alimenter l'analyseur FFT du visualiseur de spectre. */
    private fun feedSpectrum(input: ByteBuffer) {
        if (channels <= 0) return
        val dup = input.duplicate()
        dup.order(input.order())
        val frameBytes = 2 * channels
        while (dup.remaining() >= frameBytes) {
            var sum = 0
            for (c in 0 until channels) sum += dup.short.toInt()
            SpectrumData.feed((sum / channels) / 32768f)
        }
    }
}

/** Filtre biquad (Transposed Direct Form II), coefficients « RBJ cookbook » pour un peaking paramétrique. */
private class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    companion object {
        /** Peaking EQ : `f0` Hz, gain dB, largeur en octaves, fréquence d'échantillonnage. */
        fun peaking(f0: Double, gainDb: Double, bwOct: Double, fs: Int): Biquad {
            if (gainDb == 0.0 || fs <= 0) return Biquad(1.0, 0.0, 0.0, 0.0, 0.0)
            val a = Math.pow(10.0, gainDb / 40.0)
            val w0 = 2.0 * Math.PI * f0 / fs
            val cosw = cos(w0)
            val sinw = sin(w0)
            val alpha = sinw * sinh(ln(2.0) / 2.0 * bwOct * w0 / sinw)
            val a0 = 1.0 + alpha / a
            return Biquad(
                b0 = (1.0 + alpha * a) / a0,
                b1 = (-2.0 * cosw) / a0,
                b2 = (1.0 - alpha * a) / a0,
                a1 = (-2.0 * cosw) / a0,
                a2 = (1.0 - alpha / a) / a0,
            )
        }
    }
}
