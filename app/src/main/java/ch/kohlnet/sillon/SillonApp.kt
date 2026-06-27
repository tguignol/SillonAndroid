package ch.kohlnet.sillon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import ch.kohlnet.sillon.ui.theme.Sillon

/**
 * Sections de l'app — mêmes que l'iOS (cf. RootTabView / SidebarRootView).
 */
enum class SillonDestination(val label: String, val icon: ImageVector) {
    ACCUEIL("Accueil", Icons.Filled.Home),
    BIBLIOTHEQUE("Bibliothèque", Icons.Filled.LibraryMusic),
    FAVORIS("Favoris", Icons.Filled.Favorite),
    RECHERCHE("Recherche", Icons.Filled.Search),
    REGLAGES("Réglages", Icons.Filled.Settings),
}

/**
 * Racine de l'app : navigation ADAPTATIVE. `NavigationSuiteScaffold` choisit tout seul la barre du
 * bas (écran étroit = Fold plié / téléphone) ou un rail latéral (écran large = Fold déplié / tablette),
 * en miroir de l'iOS (onglets en portrait, barre latérale en paysage/macOS).
 */
@Composable
fun SillonApp() {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val current = SillonDestination.entries[index]

    // Sélection en CUIVRE (accent musical), inactifs en sourdine. Le teal reste réservé aux données
    // techniques, jamais à la navigation.
    val itemColors = NavigationSuiteItemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Sillon.colors.fondNoir,
            selectedTextColor = Sillon.colors.accentCuivre,
            indicatorColor = Sillon.colors.accentCuivre,
            unselectedIconColor = Sillon.colors.texteSourdine,
            unselectedTextColor = Sillon.colors.texteSourdine,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = Sillon.colors.fondNoir,
            selectedTextColor = Sillon.colors.accentCuivre,
            indicatorColor = Sillon.colors.accentCuivre,
            unselectedIconColor = Sillon.colors.texteSourdine,
            unselectedTextColor = Sillon.colors.texteSourdine,
        ),
        navigationDrawerItemColors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Sillon.colors.surfaceElevee,
            selectedIconColor = Sillon.colors.accentCuivre,
            selectedTextColor = Sillon.colors.accentCuivre,
            unselectedIconColor = Sillon.colors.texteSourdine,
            unselectedTextColor = Sillon.colors.texteSourdine,
        ),
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            SillonDestination.entries.forEach { dest ->
                item(
                    selected = dest == current,
                    onClick = { index = dest.ordinal },
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label, style = Sillon.type.corps) },
                    colors = itemColors,
                )
            }
        },
        containerColor = Sillon.colors.fondNoir,
    ) {
        when (current) {
            SillonDestination.ACCUEIL -> AccueilScreen()
            else -> PlaceholderScreen(current.label)
        }
    }
}

/** Écran provisoire pour les sections pas encore portées. */
@Composable
private fun PlaceholderScreen(titre: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(titre, style = Sillon.type.display, color = Sillon.colors.texteSourdine)
    }
}
