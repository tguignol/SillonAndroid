package ch.kohlnet.sillon.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import ch.kohlnet.sillon.data.SpectrumStyle
import ch.kohlnet.sillon.player.SpectrumData
import kotlin.math.cos
import kotlin.math.sin

/**
 * Anneau de visualiseur autour de la pochette (mêmes styles que l'iOS `SpectrumRingView`). Il est
 * désormais piloté par l'ANALYSE FFT temps réel du son réellement joué ([SpectrumData]) → il RÉAGIT
 * à la musique au lieu de tourner en rond. Au repos (pause) : léger souffle. OFF/OFF_SQUARE = rien.
 */
@Composable
fun SpectrumRing(
    playing: Boolean,
    style: SpectrumStyle,
    color: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val bands by SpectrumData.bands.collectAsState()
    val wave by SpectrumData.waveform.collectAsState()

    // Animation de repos (souffle léger quand rien ne joue / oscilloscope sans signal).
    val transition = rememberInfiniteTransition(label = "spectrum")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    // Historique pour la cascade (18 dernières trames, récentes à l'extérieur).
    val history = remember { mutableStateListOf<FloatArray>() }
    if (style == SpectrumStyle.CASCADE) {
        LaunchedEffect(bands) {
            history.add(bands)
            while (history.size > 18) history.removeAt(0)
        }
    }

    Canvas(modifier) {
        if (style == SpectrumStyle.OFF || style == SpectrumStyle.OFF_SQUARE) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f
        val maxBar = radius * 0.16f
        val base = radius - maxBar - 1f

        // Niveau 0..1 pour un élément i sur n autour de l'anneau : bandes MIROITÉES (grave en haut).
        fun lvl(i: Int, n: Int): Float {
            if (!playing || bands.isEmpty()) return 0.05f + 0.03f * (0.5f + 0.5f * sin(phase * 2f + i * 0.4f))
            val half = (n / 2).coerceAtLeast(1)
            val pos = if (i <= half) i else (n - i)            // 0..half..0 (symétrie)
            val bf = (pos.toFloat() / half) * (SpectrumData.BANDS - 1)
            val lo = bf.toInt().coerceIn(0, SpectrumData.BANDS - 1)
            val hi = (lo + 1).coerceAtMost(SpectrumData.BANDS - 1)
            val fr = bf - lo
            return bands[lo] + (bands[hi] - bands[lo]) * fr
        }

        when (style) {
            SpectrumStyle.CIRCULAR_BARS -> {
                val n = 96
                val stroke = (2f * Math.PI.toFloat() * base / n * 0.5f).coerceAtLeast(1.5f)
                for (i in 0 until n) {
                    val a = i.toFloat() / n * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
                    val level = lvl(i, n)
                    drawRadial(cx, cy, a, base, base + 2f + level * maxBar * 0.85f,
                        color.copy(alpha = 0.35f + 0.65f * level), stroke)
                }
            }
            SpectrumStyle.BARS -> {
                val n = 96
                val stroke = (2f * Math.PI.toFloat() * base / n * 0.62f).coerceAtLeast(2.5f)
                for (i in 0 until n) {
                    val a = i.toFloat() / n * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
                    val level = lvl(i, n)
                    val c = if (level > 0.7f) accent else color
                    drawRadial(cx, cy, a, base, base + 2f + level * maxBar * 0.9f,
                        c.copy(alpha = 0.4f + 0.6f * level), stroke)
                }
            }
            SpectrumStyle.WAVEFORM -> {
                val n = 120
                val path = closedRadialPath(cx, cy, n, -Math.PI.toFloat() / 2f) { i -> base + lvl(i, n) * maxBar }
                drawPath(path, color.copy(alpha = 0.10f))
                drawPath(path, color.copy(alpha = 0.9f), style = Stroke(width = 2.2f))
            }
            SpectrumStyle.OSCILLOSCOPE -> {
                val n = wave.size.coerceAtLeast(2)
                val mid = base - maxBar * 0.2f
                val path = Path()
                for (i in 0..n) {
                    val idx = i % n
                    val a = idx.toFloat() / n * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
                    val s = if (playing) wave[idx] else 0.25f * sin(idx * 0.5f + phase * 3f)
                    val r = mid + s * maxBar * 0.7f
                    val x = cx + cos(a) * r
                    val y = cy + sin(a) * r
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, accent.copy(alpha = 0.9f), style = Stroke(width = 1.8f))
            }
            SpectrumStyle.CASCADE -> {
                val n = 96
                val frames = history.toList()
                val rings = frames.size.coerceAtMost(18)
                for (r in 0 until rings) {
                    val frame = frames[frames.size - 1 - r]            // récent à l'extérieur
                    val r0 = base - r * (maxBar * 0.9f / 18f) - r * 1.2f
                    if (r0 <= radius * 0.2f) break
                    val t = 1f - r / 18f
                    for (i in 0 until n) {
                        val a = i.toFloat() / n * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
                        val half = n / 2
                        val pos = if (i <= half) i else (n - i)
                        val bi = (pos.toFloat() / half * (SpectrumData.BANDS - 1)).toInt().coerceIn(0, SpectrumData.BANDS - 1)
                        val level = frame.getOrElse(bi) { 0f }
                        drawRadial(cx, cy, a, r0, r0 + level * maxBar * 0.55f,
                            accent.copy(alpha = (0.12f + 0.5f * t) * (0.3f + 0.7f * level)), 2f)
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

private fun DrawScope.closedRadialPath(cx: Float, cy: Float, n: Int, startAngle: Float, radiusAt: (Int) -> Float): Path {
    val path = Path()
    for (i in 0..n) {
        val idx = i % n
        val a = idx.toFloat() / n * 2f * Math.PI.toFloat() + startAngle
        val r = radiusAt(idx)
        val x = cx + cos(a) * r
        val y = cy + sin(a) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
