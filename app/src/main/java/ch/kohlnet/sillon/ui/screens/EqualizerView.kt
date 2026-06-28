package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.data.EqualizerState
import ch.kohlnet.sillon.ui.i18n.S
import ch.kohlnet.sillon.ui.i18n.str
import ch.kohlnet.sillon.ui.theme.Sillon
import kotlin.math.roundToInt

/**
 * Égaliseur graphique (façon iOS, mode « Normal ») : curseurs verticaux par bande + on/off +
 * réinitialiser + nombre de bandes RÉGLABLE (6–12). Agit sur [EqualizerState] (appliqué en temps réel).
 */
@Composable
fun EqualizerPanel() {
    val enabled by EqualizerState.enabled.collectAsState()
    val gains by EqualizerState.gains.collectAsState()
    val bandCount by EqualizerState.bandCount.collectAsState()
    val freqs by EqualizerState.frequencies.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(Sillon.spacing.m)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = enabled,
                onCheckedChange = { EqualizerState.setEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Sillon.colors.accentCuivre),
            )
            Text(
                if (enabled) str(S.ACTIVE) else str(S.INACTIF),
                style = Sillon.type.corps,
                color = Sillon.colors.texteSourdine,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { EqualizerState.reset() }) {
                Text(str(S.REINITIALISER), style = Sillon.type.corps, color = Sillon.colors.accentCuivre)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (i in 0 until bandCount) {
                BandColumn(band = i, gain = gains.getOrElse(i) { 0f }, enabled = enabled, freq = freqs.getOrElse(i) { 0f })
            }
        }

        // Nombre de bandes (6–12).
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(str(S.BANDES), style = Sillon.type.corps, color = Sillon.colors.texteSourdine, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { EqualizerState.setBandCount(bandCount - 1) },
                enabled = bandCount > EqualizerState.MIN_BANDS,
            ) { Icon(Icons.Filled.Remove, "−", tint = Sillon.colors.texteIvoire) }
            Text("$bandCount", style = Sillon.type.corps, color = Sillon.colors.texteIvoire)
            IconButton(
                onClick = { EqualizerState.setBandCount(bandCount + 1) },
                enabled = bandCount < EqualizerState.MAX_BANDS,
            ) { Icon(Icons.Filled.Add, "+", tint = Sillon.colors.texteIvoire) }
        }
    }
}

@Composable
private fun RowScope.BandColumn(band: Int, gain: Float, enabled: Boolean, freq: Float) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
    ) {
        val g = gain.roundToInt()
        Text(
            text = if (g > 0) "+$g" else "$g",
            style = Sillon.type.technique,
            color = if (enabled) Sillon.colors.signalTeal else Sillon.colors.texteSourdine,
            fontSize = 9.sp,
        )
        VerticalEqSlider(
            value = gain,
            onValueChange = { EqualizerState.setGain(band, it) },
            enabled = enabled,
            modifier = Modifier.height(170.dp).fillMaxWidth(),
        )
        Text(
            text = EqualizerState.frequencyLabel(freq),
            style = Sillon.type.technique,
            color = Sillon.colors.texteSourdine,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Curseur VERTICAL custom (drag/tap fiables) : haut = +12 dB, centre = 0, bas = −12 dB. */
@Composable
private fun VerticalEqSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val min = EqualizerState.MIN_GAIN
    val max = EqualizerState.MAX_GAIN
    val track = Sillon.colors.texteSourdine.copy(alpha = 0.4f)
    val accent = if (enabled) Sillon.colors.accentCuivre else Sillon.colors.texteSourdine

    BoxWithConstraints(modifier) {
        val hPx = with(LocalDensity.current) { maxHeight.toPx() }
        fun valueAt(y: Float) = (min + (1f - (y / hPx).coerceIn(0f, 1f)) * (max - min)).coerceIn(min, max)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { onValueChange(valueAt(it.y)) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { change, _ ->
                        onValueChange(valueAt(change.position.y))
                        change.consume()
                    }
                },
        ) {
            val cx = size.width / 2f
            val w = 4.dp.toPx()
            fun yFor(v: Float) = size.height * (1f - (v - min) / (max - min))
            drawLine(track, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(accent, Offset(cx, yFor(0f)), Offset(cx, yFor(value)), strokeWidth = w, cap = StrokeCap.Round)
            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(cx, yFor(value)))
            drawCircle(accent, radius = 8.dp.toPx(), center = Offset(cx, yFor(value)), style = Stroke(width = 2.dp.toPx()))
        }
    }
}
