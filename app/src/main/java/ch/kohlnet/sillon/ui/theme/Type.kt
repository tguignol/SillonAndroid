package ch.kohlnet.sillon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.R

/**
 * Typographie Sillon — miroir de `Typo` (iOS `Theme.swift`).
 *
 * iOS utilise les polices SYSTÈME d'Apple : New York (serif), SF Mono, SF Pro. Sur Android, on
 * EMBARQUE des équivalents libres (OFL) pour matcher le rendu : **Source Serif 4** (≈ New York) et
 * **JetBrains Mono** (≈ SF Mono), dans `res/font/`. Le corps reste sur la police système (≈ SF Pro).
 *
 * Tailles : iOS = Dynamic Type ; on garde des `sp` (et non des `dp`) pour respecter l'accessibilité.
 */
val SillonSerif = FontFamily(
    Font(R.font.source_serif_regular, FontWeight.Normal),
    Font(R.font.source_serif_medium, FontWeight.Medium),
    Font(R.font.source_serif_semibold, FontWeight.SemiBold),
)
val SillonMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
)

/** Tokens typographiques nommés, comme l'enum `Typo` iOS. Accès via `Sillon.type`. */
object SillonType {
    /** Titres d'album, nom d'artiste en grand. */
    val display = TextStyle(fontFamily = SillonSerif, fontWeight = FontWeight.Normal, fontSize = 28.sp)

    /** Sous-titres serif. */
    val displaySmall = TextStyle(fontFamily = SillonSerif, fontWeight = FontWeight.Medium, fontSize = 20.sp)

    /** Ligne de paroles EN COURS de lecture (un cran au-dessus, façon Apple Music). */
    val paroleActive = TextStyle(fontFamily = SillonSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)

    /** Corps / UI général (SF Pro côté iOS). */
    val corps = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 17.sp)

    /** Donnée technique : bitrate, codec, dB, horodatage de sync (monospace + teal). */
    val technique = TextStyle(fontFamily = SillonMono, fontWeight = FontWeight.Normal, fontSize = 12.sp)
}

/** Typographie Material 3 dérivée des tokens Sillon (pour les composants standard). */
val SillonTypography = Typography(
    titleLarge = SillonType.display,
    titleMedium = SillonType.displaySmall,
    bodyLarge = SillonType.corps,
    bodyMedium = SillonType.corps,
    labelSmall = SillonType.technique,
)
