package ch.kohlnet.sillon.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import ch.kohlnet.sillon.data.SpectrumStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * Anneau de visualiseur autour de la pochette (mêmes styles que l'iOS `SpectrumRingView`) :
 * barres circulaires fines / barres épaisses / ondulation / cascade / oscilloscope. DÉCORATIF/animé
 * (un vrai FFT demanderait la permission micro) ; au repos les barres se calment. OFF/OFF_SQUARE = rien.
 */
@Composable
fun SpectrumRing(
    playing: Boolean,
    style: SpectrumStyle,
    color: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "spectrum")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    Canvas(modifier) {
        if (style == SpectrumStyle.OFF || style == SpectrumStyle.OFF_SQUARE) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f
        val maxBar = radius * 0.16f
        val base = radius - maxBar - 1f

        // Amplitude décorative par bande (0.10 au repos).
        fun amp(i: Int): Float {
            if (!playing) return 0.10f
            val s1 = 0.5f + 0.5f * sin(phase * 3f + i * 0.55f)
            val s2 = 0.5f + 0.5f * sin(phase * 2f + i * 0.27f)
            return 0.18f + 0.82f * s1 * s2
        }

        when (style) {
            SpectrumStyle.CIRCULAR_BARS -> {
                val n = 60
                val stroke = (2f * Math.PI.toFloat() * base / n * 0.5f).coerceAtLeast(2f)
                for (i in 0 until n) {
                    val a = i.toFloat() / n * 2f * Math.PI.toFloat()
                    val len = maxBar * amp(i)
                    drawRadial(cx, cy, a, base, base + len, if (amp(i) > 0.78f) accent else color, stroke)
                }
            }
            SpectrumStyle.BARS -> {
                val n = 40
                val stroke = (2f * Math.PI.toFloat() * base / n * 0.62f).coerceAtLeast(3f)
                for (i in 0 until n) {
                    val a = i.toFloat() / n * 2f * Math.PI.toFloat()
                    val amp = amp(i)
                    drawRadial(cx, cy, a, base, base + maxBar * amp, if (amp >= 0.7f) accent else color, stroke)
                }
            }
            SpectrumStyle.WAVEFORM -> {
                val n = 90
                val path = closedRadialPath(cx, cy, n) { i -> base + maxBar * amp(i) }
                drawPath(path, color.copy(alpha = 0.10f))
                drawPath(path, color.copy(alpha = 0.9f), style = Stroke(width = 2.5f))
            }
            SpectrumStyle.OSCILLOSCOPE -> {
                val n = 120
                val mid = base + maxBar * 0.5f
                val path = closedRadialPath(cx, cy, n) { i ->
                    mid + maxBar * 0.45f * sin(i * 0.6f + (if (playing) phase * 3f else 0f))
                }
                drawPath(path, accent.copy(alpha = 0.9f), style = Stroke(width = 2f))
            }
            SpectrumStyle.CASCADE -> {
                val n = 48
                for (ring in 0 until 5) {
                    val r0 = base - ring * (maxBar * 1.1f)
                    if (r0 <= 0f) break
                    val alpha = 0.55f - ring * 0.09f
                    val stroke = 2.5f
                    for (i in 0 until n) {
                        val a = i.toFloat() / n * 2f * Math.PI.toFloat()
                        val len = maxBar * 0.7f * amp(i + ring * 3)
                        drawRadial(cx, cy, a, r0, r0 + len, accent.copy(alpha = alpha.coerceAtLeast(0.1f)), stroke)
                    }
                }
            }
            else -> {}
        }
    }
}

private fun DrawScope.drawRadial(cx: Float, cy: Float, angle: Float, r0: Float, r1: Float, color: Color, stroke: Float) {
    drawLine(
        color,
        Offset(cx + cos(angle) * r0, cy + sin(angle) * r0),
        Offset(cx + cos(angle) * r1, cy + sin(angle) * r1),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.closedRadialPath(cx: Float, cy: Float, n: Int, radiusAt: (Int) -> Float): Path {
    val path = Path()
    for (i in 0..n) {
        val idx = i % n
        val a = idx.toFloat() / n * 2f * Math.PI.toFloat()
        val r = radiusAt(idx)
        val x = cx + cos(a) * r
        val y = cy + sin(a) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
