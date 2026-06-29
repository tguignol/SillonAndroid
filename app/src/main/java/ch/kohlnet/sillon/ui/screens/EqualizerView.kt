package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.data.EQMode
import ch.kohlnet.sillon.data.EqualizerState
import ch.kohlnet.sillon.ui.components.ThinSlider
import ch.kohlnet.sillon.ui.components.sillonSegmentedColors
import ch.kohlnet.sillon.ui.i18n.S
import ch.kohlnet.sillon.ui.i18n.str
import ch.kohlnet.sillon.ui.theme.Sillon
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Égaliseur (façon iOS) avec 3 MODES sélectionnables : « Normal » (curseurs verticaux), « Paramétrique »
 * (gain/fréquence/largeur par bande), « Graphique » (courbe de réponse + poignées draggables). On/off,
 * réinitialiser, nombre de bandes RÉGLABLE (6–12). Agit sur [EqualizerState] (appliqué en temps réel).
 */
@Composable
fun EqualizerPanel() {
    val enabled by EqualizerState.enabled.collectAsState()
    val mode by EqualizerState.mode.collectAsState()
    val gains by EqualizerState.gains.collectAsState()
    val bandCount by EqualizerState.bandCount.collectAsState()
    val freqs by EqualizerState.frequencies.collectAsState()
    val bws by EqualizerState.bandwidths.collectAsState()

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Sillon.spacing.m)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = enabled,
                onCheckedChange = { EqualizerState.setEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Sillon.colors.accentCuivre),
                modifier = Modifier.scale(0.8f), // affiné : un cran plus petit, plus élégant
            )
            Spacer(Modifier.width(Sillon.spacing.m)) // léger espace entre la bascule et le libellé
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

        // Préampli (volume GLOBAL) : monte le niveau de sortie au-delà du plafond système. Actif même
        // quand l'EQ est désactivé ; limiteur doux côté processeur. 0 dB = aucun boost.
        val preamp by EqualizerState.preampDb.collectAsState()
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Préampli", style = Sillon.type.corps, color = Sillon.colors.texteSourdine)
            Spacer(Modifier.width(Sillon.spacing.m))
            ThinSlider(
                value = preamp,
                onValueChange = { EqualizerState.setPreamp(it) },
                valueRange = EqualizerState.MIN_PREAMP..EqualizerState.MAX_PREAMP,
                activeColor = Sillon.colors.accentCuivre,
                inactiveColor = Sillon.colors.texteSourdine.copy(alpha = 0.4f),
                thumbColor = Sillon.colors.accentCuivre,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Sillon.spacing.s))
            Text("+${preamp.roundToInt()} dB", style = Sillon.type.technique, color = Sillon.colors.texteIvoire)
        }

        // Sélecteur de mode (la « ligne de boutons » pour passer de l'un à l'autre).
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes = listOf(EQMode.NORMAL to "Normal", EQMode.PARAMETRIC to "Paramétrique", EQMode.GRAPHIC to "Graphique")
            modes.forEachIndexed { i, (m, label) ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { EqualizerState.setMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    colors = sillonSegmentedColors(),
                    icon = {},
                ) { Text(label, style = Sillon.type.corps.copy(fontSize = 12.sp), maxLines = 1) }
            }
        }

        when (mode) {
            EQMode.NORMAL -> NormalEditor(bandCount, gains, enabled, freqs)
            EQMode.PARAMETRIC -> ParametricEditor(bandCount, gains, freqs, bws, enabled)
            EQMode.GRAPHIC -> GraphicEditor(bandCount, gains, freqs, bws, enabled)
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

        // Presets (réglages enregistrés) : tap = appliquer, appui long = supprimer.
        val presets by EqualizerState.presets.collectAsState()
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Presets", style = Sillon.type.corps, color = Sillon.colors.texteSourdine, modifier = Modifier.weight(1f))
            TextButton(onClick = { EqualizerState.savePreset() }) {
                Text("Enregistrer", style = Sillon.type.corps, color = Sillon.colors.accentCuivre)
            }
        }
        if (presets.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s)) {
                itemsIndexed(presets) { i, p ->
                    PresetChip(p.name, onApply = { EqualizerState.applyPreset(p) }, onDelete = { EqualizerState.deletePreset(i) })
                }
            }
            Text(
                "Appui long sur un preset pour le supprimer.",
                style = Sillon.type.technique,
                color = Sillon.colors.texteSourdine.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(name: String, onApply: () -> Unit, onDelete: () -> Unit) {
    Text(
        text = name,
        style = Sillon.type.corps.copy(fontSize = 12.sp),
        color = Sillon.colors.texteIvoire,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Sillon.colors.surfaceElevee)
            .combinedClickable(onClick = onApply, onLongClick = onDelete)
            .padding(horizontal = Sillon.spacing.m, vertical = Sillon.spacing.xs),
    )
}

/* ----------------------------- Mode NORMAL ----------------------------- */

@Composable
private fun NormalEditor(bandCount: Int, gains: FloatArray, enabled: Boolean, freqs: FloatArray) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until bandCount) {
            BandColumn(band = i, gain = gains.getOrElse(i) { 0f }, enabled = enabled, freq = freqs.getOrElse(i) { 0f })
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

/* --------------------------- Mode PARAMÉTRIQUE --------------------------- */

@Composable
private fun ParametricEditor(bandCount: Int, gains: FloatArray, freqs: FloatArray, bws: FloatArray, enabled: Boolean) {
    val colors = SliderDefaults.colors(
        thumbColor = Sillon.colors.accentCuivre,
        activeTrackColor = Sillon.colors.accentCuivre,
        inactiveTrackColor = Sillon.colors.texteSourdine.copy(alpha = 0.4f),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Sillon.spacing.s)) {
        for (i in 0 until bandCount) {
            val gain = gains.getOrElse(i) { 0f }
            val freq = freqs.getOrElse(i) { 1000f }
            val bw = bws.getOrElse(i) { 1f }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
                    .background(Sillon.colors.surfaceElevee)
                    .padding(Sillon.spacing.m),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val gs = gain.roundToInt().let { if (it > 0) "+$it" else "$it" }
                Text(
                    "Bande ${i + 1}   ·   ${EqualizerState.frequencyLabel(freq)} Hz · $gs dB · ${"%.1f".format(bw)} oct",
                    style = Sillon.type.technique,
                    color = Sillon.colors.texteIvoire,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ParamSlider("Gain", gain, EqualizerState.MIN_GAIN..EqualizerState.MAX_GAIN, enabled, colors) {
                    EqualizerState.setGain(i, it)
                }
                // Fréquence sur échelle logarithmique (20 Hz–20 kHz).
                val logMin = ln(EqualizerState.MIN_FREQ); val logMax = ln(EqualizerState.MAX_FREQ)
                ParamSlider("Fréq.", ln(freq.coerceIn(EqualizerState.MIN_FREQ, EqualizerState.MAX_FREQ)), logMin..logMax, enabled, colors) {
                    EqualizerState.setFrequency(i, exp(it))
                }
                ParamSlider("Largeur", bw, 0.1f..3.0f, enabled, colors) {
                    EqualizerState.setBandwidth(i, it)
                }
            }
        }
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    colors: androidx.compose.material3.SliderColors,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = Sillon.type.technique,
            color = Sillon.colors.texteSourdine,
            fontSize = 10.sp,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            colors = colors,
            modifier = Modifier.weight(1f),
        )
    }
}

/* ---------------------------- Mode GRAPHIQUE ---------------------------- */

@Composable
private fun GraphicEditor(bandCount: Int, gains: FloatArray, freqs: FloatArray, bws: FloatArray, enabled: Boolean) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val curveColor = Sillon.colors.accentCuivre
    val gridColor = Sillon.colors.texteSourdine.copy(alpha = 0.35f)
    val handleSel = Sillon.colors.signalTeal
    val bg = Sillon.colors.surfaceElevee
    val logMin = log10(20.0)
    val logMax = log10(20_000.0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
            .background(bg),
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        fun xOf(f: Double): Float = ((log10(f) - logMin) / (logMax - logMin) * wPx).toFloat()
        fun fOf(x: Float): Double = 10.0.pow(logMin + (x / wPx).coerceIn(0f, 1f) * (logMax - logMin))
        fun yOf(g: Double): Float = ((1.0 - (g + 12.0) / 24.0) * hPx).toFloat()
        fun gOf(y: Float): Double = 12.0 - (y / hPx).coerceIn(0f, 1f) * 24.0
        fun nearest(off: Offset): Int {
            var best = 0; var bestD = Float.MAX_VALUE
            for (i in 0 until bandCount) {
                val dx = off.x - xOf(freqs.getOrElse(i) { 1000f }.toDouble())
                val dy = off.y - yOf(gains.getOrElse(i) { 0f }.toDouble())
                val d = dx * dx + dy * dy
                if (d < bestD) { bestD = d; best = i }
            }
            return best
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, bandCount) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { off -> selected = nearest(off) }
                }
                .pointerInput(enabled, bandCount) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { off -> selected = nearest(off) },
                        onDrag = { change, _ ->
                            selected?.let { b ->
                                EqualizerState.setFrequency(b, fOf(change.position.x).toFloat())
                                EqualizerState.setGain(b, gOf(change.position.y).toFloat())
                            }
                            change.consume()
                        },
                    )
                },
        ) {
            // Ligne 0 dB (pointillés) + repères verticaux 100 Hz / 1 kHz / 10 kHz.
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            drawLine(gridColor, Offset(0f, yOf(0.0)), Offset(wPx, yOf(0.0)), strokeWidth = 1.5f, pathEffect = dash)
            for (f in listOf(100.0, 1000.0, 10_000.0)) {
                val x = xOf(f)
                drawLine(gridColor.copy(alpha = 0.2f), Offset(x, 0f), Offset(x, hPx), strokeWidth = 1f)
            }

            // Courbe de réponse (somme de gaussiennes en log2, approximation visuelle façon iOS).
            val line = Path()
            val fill = Path()
            val zeroY = yOf(0.0)
            var px = 0f
            var first = true
            val stepPx = wPx / 120f
            while (px <= wPx) {
                val g = responseGain(fOf(px), freqs, gains, bws).coerceIn(-12.0, 12.0)
                val y = yOf(g)
                if (first) { line.moveTo(px, y); fill.moveTo(px, zeroY); fill.lineTo(px, y); first = false }
                else { line.lineTo(px, y); fill.lineTo(px, y) }
                px += stepPx
            }
            fill.lineTo(wPx, zeroY)
            fill.close()
            drawPath(fill, curveColor.copy(alpha = 0.15f))
            drawPath(line, curveColor, style = Stroke(width = 2.5.dp.toPx()))

            // Poignées (une par bande).
            for (i in 0 until bandCount) {
                val x = xOf(freqs.getOrElse(i) { 1000f }.toDouble())
                val y = yOf(gains.getOrElse(i) { 0f }.toDouble())
                val sel = i == selected
                val r = if (sel) 11.dp.toPx() else 8.dp.toPx()
                drawCircle(if (sel) handleSel else curveColor, radius = r, center = Offset(x, y))
                drawCircle(Color.White, radius = r, center = Offset(x, y), style = Stroke(width = 1.5.dp.toPx()))
            }

            // Cadre arrondi.
            drawRoundRect(
                color = gridColor.copy(alpha = 0.25f),
                cornerRadius = CornerRadius(with(density) { Sillon.spacing.cardCorner.toPx() }),
                style = Stroke(width = 1f),
            )
        }
    }

    // Largeur de la bande sélectionnée.
    val sel = selected
    if (sel != null && sel < bandCount) {
        val colors = SliderDefaults.colors(
            thumbColor = Sillon.colors.accentCuivre,
            activeTrackColor = Sillon.colors.accentCuivre,
            inactiveTrackColor = Sillon.colors.texteSourdine.copy(alpha = 0.4f),
        )
        Column {
            Text(
                "Largeur · bande ${sel + 1} : ${"%.1f".format(bws.getOrElse(sel) { 1f })} oct",
                style = Sillon.type.technique,
                color = Sillon.colors.texteSourdine,
                fontSize = 10.sp,
            )
            Slider(
                value = bws.getOrElse(sel) { 1f },
                onValueChange = { EqualizerState.setBandwidth(sel, it) },
                valueRange = 0.1f..3.0f,
                enabled = enabled,
                colors = colors,
            )
        }
    } else {
        Text(
            "Touchez une poignée pour régler sa largeur.",
            style = Sillon.type.technique,
            color = Sillon.colors.texteSourdine.copy(alpha = 0.7f),
            fontSize = 10.sp,
        )
    }
}

/** Réponse approchée = somme de cloches gaussiennes en log2-fréquence (comme l'iOS `EQCurveView`). */
private fun responseGain(f: Double, freqs: FloatArray, gains: FloatArray, bws: FloatArray): Double {
    var sum = 0.0
    val n = minOf(freqs.size, gains.size)
    for (i in 0 until n) {
        val fc = freqs[i].toDouble()
        val g = gains[i].toDouble()
        val bw = bws.getOrElse(i) { 1f }.toDouble()
        val d = (log2(f) - log2(fc)) / maxOf(0.2, bw)
        sum += g * exp(-0.5 * d * d)
    }
    return sum
}

private fun log2(x: Double): Double = ln(x) / ln(2.0)
