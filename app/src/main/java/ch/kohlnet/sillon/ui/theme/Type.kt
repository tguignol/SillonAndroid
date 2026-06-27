package ch.kohlnet.sillon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typographie Sillon — miroir de `Typo` (iOS `Theme.swift`).
 *
 * iOS utilise les polices SYSTÈME d'Apple : New York (serif), SF Mono, SF Pro. Sur Android,
 * pour matcher au plus près New York/SF Mono, on embarquera plus tard **Source Serif 4** et une
 * mono (JetBrains/Roboto Mono) dans `res/font/`. Pour l'instant on s'appuie sur les familles
 * système (`FontFamily.Serif` = Noto Serif, `FontFamily.Monospace`), déjà proches visuellement.
 *
 * Tailles : iOS = Dynamic Type ; on garde des `sp` (et non des `dp`) pour respecter l'accessibilité.
 */
val SillonSerif: FontFamily = FontFamily.Serif      // ≈ New York (à remplacer par Source Serif 4)
val SillonMono: FontFamily = FontFamily.Monospace   // ≈ SF Mono (à remplacer par JetBrains Mono)

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
