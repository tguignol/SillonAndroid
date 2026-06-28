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

    private fun recompute() {
        val gains = EqualizerState.gains.value
        val freqs = EqualizerState.frequencies
        bypass = !EqualizerState.enabled.value
        biquads = Array(channels) { _ ->
            Array(EqualizerState.BAND_COUNT) { b ->
                Biquad.peaking(freqs[b].toDouble(), gains[b].toDouble(), 1.0, sampleRate)
            }
        }
        lastGen = EqualizerState.generation
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (EqualizerState.generation != lastGen) recompute()
        val size = inputBuffer.remaining()
        val output = replaceOutputBuffer(size)
        if (bypass || channels <= 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        var ch = 0
        while (inputBuffer.hasRemaining()) {
            var y = inputBuffer.short.toDouble()
            for (bq in biquads[ch]) y = bq.process(y)
            output.putShort(y.coerceIn(-32768.0, 32767.0).roundToInt().toShort())
            ch = (ch + 1) % channels
        }
        output.flip()
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
