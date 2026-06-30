package ch.kohlnet.sillon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Curseur horizontal FIN (barre de progression / volume), façon iOS : rail mince + petite poignée. */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val min = valueRange.start
    val max = valueRange.endInclusive
    // Valeur SUIVIE PENDANT le glissement : le curseur suit le DOIGT (pas `value`, qui peut être faux/laggy
    // pendant le buffering d'un seek) → il reste là où on lâche, ne « saute » plus en fin de titre.
    var dragValue by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(modifier.height(28.dp)) { // zone tactile plus haute → plus facile à attraper
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        fun valueAt(x: Float) = (min + (x / wPx).coerceIn(0f, 1f) * (max - min)).coerceIn(min, max)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // GLISSER uniquement (pas de « tap-to-seek »). Pendant le glissement, on mémorise la valeur
                // sous le doigt (dragValue) et on l'envoie ; à la fin, on confirme et on relâche le suivi.
                .pointerInput(enabled, min, max, wPx) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { val v = valueAt(it.x); dragValue = v; onValueChange(v) },
                        onDragEnd = { dragValue?.let(onValueChange); dragValue = null },
                        onDragCancel = { dragValue = null },
                    ) { change, _ ->
                        val v = valueAt(change.position.x)
                        dragValue = v
                        onValueChange(v)
                        change.consume()
                    }
                },
        ) {
            val cy = size.height / 2f
            val span = max - min
            val shown = dragValue ?: value // glissement → doigt ; sinon → position de lecture
            val frac = if (span != 0f) ((shown - min) / span).coerceIn(0f, 1f) else 0f
            val xv = size.width * frac
            val h = 2.5.dp.toPx()
            drawLine(inactiveColor, Offset(0f, cy), Offset(size.width, cy), strokeWidth = h, cap = StrokeCap.Round)
            drawLine(activeColor, Offset(0f, cy), Offset(xv, cy), strokeWidth = h, cap = StrokeCap.Round)
            drawCircle(thumbColor, radius = 6.dp.toPx(), center = Offset(xv, cy))
        }
    }
}
