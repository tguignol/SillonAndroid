package ch.kohlnet.sillon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Couleurs sémantiques nommées de Sillon (au-delà des slots Material), façon enum `Palette` iOS. */
data class SillonColors(
    val fondNoir: Color,
    val surfaceElevee: Color,
    val accentCuivre: Color,
    val signalTeal: Color,
    val texteIvoire: Color,
    val texteSourdine: Color,
)

private val DarkSillonColors = SillonColors(
    fondNoir = FondNoirDark,
    surfaceElevee = SurfaceEleveeDark,
    accentCuivre = AccentCuivreDark,
    signalTeal = SignalTealDark,
    texteIvoire = TexteIvoireDark,
    texteSourdine = TexteSourdineDark,
)

private val LightSillonColors = SillonColors(
    fondNoir = FondNoirLight,
    surfaceElevee = SurfaceEleveeLight,
    accentCuivre = AccentCuivreLight,
    signalTeal = SignalTealLight,
    texteIvoire = TexteIvoireLight,
    texteSourdine = TexteSourdineLight,
)

private val LocalSillonColors = staticCompositionLocalOf { DarkSillonColors }

private fun darkScheme() = darkColorScheme(
    primary = AccentCuivreDark,
    onPrimary = FondNoirDark,
    secondary = SignalTealDark,
    onSecondary = FondNoirDark,
    tertiary = SignalTealDark,
    background = FondNoirDark,
    onBackground = TexteIvoireDark,
    surface = FondNoirDark,
    onSurface = TexteIvoireDark,
    surfaceVariant = SurfaceEleveeDark,
    onSurfaceVariant = TexteSourdineDark,
)

private fun lightScheme() = lightColorScheme(
    primary = AccentCuivreLight,
    onPrimary = Color.White,
    secondary = SignalTealLight,
    onSecondary = Color.White,
    tertiary = SignalTealLight,
    background = FondNoirLight,
    onBackground = TexteIvoireLight,
    surface = FondNoirLight,
    onSurface = TexteIvoireLight,
    surfaceVariant = SurfaceEleveeLight,
    onSurfaceVariant = TexteSourdineLight,
)

/**
 * Thème racine de Sillon. PAS de couleur dynamique Material You — on garde l'identité cuivre/teal.
 * Suit l'apparence système (≈ « Automatique » iOS) ; design pensé d'abord pour le mode sombre.
 */
@Composable
fun SillonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val sillonColors = if (darkTheme) DarkSillonColors else LightSillonColors
    val colorScheme = if (darkTheme) darkScheme() else lightScheme()
    CompositionLocalProvider(LocalSillonColors provides sillonColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SillonTypography,
            content = content,
        )
    }
}

/** Accès au design system Sillon (façon `MaterialTheme`) : `Sillon.colors`, `Sillon.type`, `Sillon.spacing`. */
object Sillon {
    val colors: SillonColors
        @Composable @ReadOnlyComposable get() = LocalSillonColors.current

    val type get() = SillonType
    val spacing get() = Spacing
}
