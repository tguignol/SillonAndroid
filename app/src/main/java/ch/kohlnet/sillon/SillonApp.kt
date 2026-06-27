package ch.kohlnet.sillon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.screens.AccueilScreen
import ch.kohlnet.sillon.ui.screens.BibliothequeScreen
import ch.kohlnet.sillon.ui.screens.FullPlayerScreen
import ch.kohlnet.sillon.ui.screens.ServerConnectionScreen
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
 * Racine de l'app : navigation ADAPTATIVE (`NavigationSuiteScaffold` : barre du bas si écran étroit
 * = Fold plié/téléphone → version iPhone ; rail latéral si large = Fold déplié/tablette → version iPad).
 * Par-dessus, le lecteur plein écran s'ouvre depuis la barre « Lecture en cours ».
 */
@Composable
fun SillonApp() {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val current = SillonDestination.entries[index]
    var showPlayer by remember { mutableStateOf(false) }
    val playingTrack by PlayerController.current.collectAsState()

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

    Box(Modifier.fillMaxSize()) {
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
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (current) {
                        SillonDestination.ACCUEIL -> AccueilScreen()
                        SillonDestination.BIBLIOTHEQUE -> BibliothequeScreen()
                        SillonDestination.REGLAGES -> ServerConnectionScreen()
                        else -> PlaceholderScreen(current.label)
                    }
                }
                NowPlayingBar(onOpen = { showPlayer = true })
            }
        }

        if (showPlayer && playingTrack != null) {
            FullPlayerScreen(onClose = { showPlayer = false })
        }
    }
}

/** Barre « Lecture en cours » (façon iOS) : titre/artiste + lecture/pause ; tap = lecteur plein écran. */
@Composable
private fun NowPlayingBar(onOpen: () -> Unit) {
    val track by PlayerController.current.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val t = track ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Sillon.colors.surfaceElevee)
            .clickable(onClick = onOpen)
            .navigationBarsPadding()
            .padding(horizontal = Sillon.spacing.l, vertical = Sillon.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                style = Sillon.type.corps,
                color = Sillon.colors.texteIvoire,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (t.artist.isNotBlank()) {
                Text(
                    t.artist,
                    style = Sillon.type.corps.copy(fontSize = 13.sp),
                    color = Sillon.colors.texteSourdine,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { PlayerController.togglePlayPause() }) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Lecture",
                tint = Sillon.colors.accentCuivre,
            )
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
