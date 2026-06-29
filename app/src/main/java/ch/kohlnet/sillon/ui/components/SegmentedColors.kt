package ch.kohlnet.sillon.ui.components

import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ch.kohlnet.sillon.ui.theme.Sillon

/**
 * Couleurs des sélecteurs segmentés façon Sillon : sélection en CUIVRE (au lieu du violet Material par
 * défaut), pour rester cohérent avec le reste de l'app. À combiner avec `icon = {}` sur chaque
 * `SegmentedButton` pour retirer la coche « ✓ ».
 */
@Composable
fun sillonSegmentedColors(): SegmentedButtonColors = SegmentedButtonDefaults.colors(
    activeContainerColor = Sillon.colors.accentCuivre.copy(alpha = 0.22f),
    activeContentColor = Sillon.colors.accentCuivre,
    activeBorderColor = Sillon.colors.accentCuivre.copy(alpha = 0.55f),
    inactiveContainerColor = Color.Transparent,
    inactiveContentColor = Sillon.colors.texteSourdine,
    inactiveBorderColor = Sillon.colors.texteSourdine.copy(alpha = 0.4f),
)
