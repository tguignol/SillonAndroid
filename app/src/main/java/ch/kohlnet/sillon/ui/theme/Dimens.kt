package ch.kohlnet.sillon.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Espacements Sillon — miroir de `Spacing` (iOS `Theme.swift`). 1 point iOS = 1 dp (repris à l'identique).
 */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Rayon d'angle standard des cartes (pochettes, sections). */
    val cardCorner = 10.dp
}

/**
 * Dégradé de remplacement déterministe pour les pochettes manquantes — miroir de
 * `Palette.placeholderGradient(seed:)`. Même album → toujours la même couleur (hash djb2 stable,
 * PAS le `hashValue` randomisé par process). Diagonale haut-gauche → bas-droite.
 */
fun placeholderBrush(seed: String): Brush {
    var hash = 5381UL
    for (c in seed) hash = hash * 33UL + c.code.toULong()   // djb2 ; débordement ULong assumé
    val hue = (hash % 360UL).toFloat()                      // 0..359
    val base = Color.hsv(hue, 0.28f, 0.34f)
    val dark = Color.hsv(hue, 0.35f, 0.18f)
    return Brush.linearGradient(listOf(base, dark))
}
