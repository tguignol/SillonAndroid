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
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.cos
import kotlin.math.sin

/**
 * Anneau de spectre autour de la pochette (façon iOS `SpectrumRingView`). Barres réparties en cercle,
 * cuivre (teal sur les pics). DÉCORATIF/animé pour l'instant (un vrai FFT temps réel demanderait la
 * permission micro sur Android) ; au repos les barres se calment.
 */
@Composable
fun SpectrumRing(
    playing: Boolean,
    color: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    bars: Int = 60,
) {
    val transition = rememberInfiniteTransition(label = "spectrum")
    // Multiplicateurs ENTIERS → boucle sans saut à la jonction 0/2π.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f
        val maxBar = radius * 0.16f
        val base = radius - maxBar - 1f
        val stroke = (2f * Math.PI * base / bars * 0.5f).toFloat().coerceAtLeast(2f)

        for (i in 0 until bars) {
            val a = (i.toFloat() / bars) * 2f * Math.PI.toFloat()
            val amp = if (playing) {
                val s1 = 0.5f + 0.5f * sin(phase * 3f + i * 0.55f)
                val s2 = 0.5f + 0.5f * sin(phase * 2f + i * 0.27f)
                (0.18f + 0.82f * s1 * s2)
            } else {
                0.10f
            }
            val len = maxBar * amp
            val sx = cx + cos(a) * base
            val sy = cy + sin(a) * base
            val ex = cx + cos(a) * (base + len)
            val ey = cy + sin(a) * (base + len)
            drawLine(
                color = if (amp > 0.78f) accent else color,
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
