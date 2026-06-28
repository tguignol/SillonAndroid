package ch.kohlnet.sillon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
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

    BoxWithConstraints(modifier.height(20.dp)) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        fun valueAt(x: Float) = (min + (x / wPx).coerceIn(0f, 1f) * (max - min)).coerceIn(min, max)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { onValueChange(valueAt(it.x)) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures { change, _ ->
                        onValueChange(valueAt(change.position.x))
                        change.consume()
                    }
                },
        ) {
            val cy = size.height / 2f
            val span = max - min
            val frac = if (span != 0f) ((value - min) / span).coerceIn(0f, 1f) else 0f
            val xv = size.width * frac
            val h = 2.5.dp.toPx()
            drawLine(inactiveColor, Offset(0f, cy), Offset(size.width, cy), strokeWidth = h, cap = StrokeCap.Round)
            drawLine(activeColor, Offset(0f, cy), Offset(xv, cy), strokeWidth = h, cap = StrokeCap.Round)
            drawCircle(thumbColor, radius = 6.dp.toPx(), center = Offset(xv, cy))
        }
    }
}
