package ch.kohlnet.sillon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.i18n.S
import ch.kohlnet.sillon.ui.i18n.str
import ch.kohlnet.sillon.ui.screens.AccueilScreen
import ch.kohlnet.sillon.ui.screens.BibliothequeScreen
import ch.kohlnet.sillon.ui.screens.FavorisScreen
import ch.kohlnet.sillon.ui.screens.FullPlayerScreen
import ch.kohlnet.sillon.ui.screens.RechercheScreen
import ch.kohlnet.sillon.ui.screens.ServerConnectionScreen
import ch.kohlnet.sillon.ui.theme.Sillon

/** Sections de l'app — mêmes que l'iOS (cf. RootTabView / SidebarRootView). */
enum class SillonDestination(val titleKey: S, val icon: ImageVector) {
    ACCUEIL(S.ACCUEIL, Icons.Filled.Home),
    BIBLIOTHEQUE(S.BIBLIOTHEQUE, Icons.Filled.LibraryMusic),
    FAVORIS(S.FAVORIS, Icons.Filled.Favorite),
    RECHERCHE(S.RECHERCHE, Icons.Filled.Search),
    REGLAGES(S.REGLAGES, Icons.Filled.Settings),
}

/**
 * Racine ADAPTATIVE (façon iOS) :
 * - écran ÉTROIT (Fold plié / téléphone → iPhone) : barre d'onglets EN BAS.
 * - écran LARGE (Fold déplié / tablette → iPadOS) : BARRE LATÉRALE REPLIABLE (bouton hamburger,
 *   comme `NavigationSplitView`). Par-dessus, le lecteur plein écran s'ouvre depuis la barre « Lecture ».
 */
@Composable
fun SillonApp() {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val current = SillonDestination.entries[index]
    var showPlayer by remember { mutableStateOf(false) }
    var sidebarVisible by rememberSaveable { mutableStateOf(true) }
    val playingTrack by PlayerController.current.collectAsState()

    Box(Modifier.fillMaxSize().background(Sillon.colors.fondNoir)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = sidebarVisible) {
                        SillonRail(current) { index = it.ordinal }
                    }
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            Row(Modifier.fillMaxSize()) {
                                // « Toolbar » de la colonne de détail : bouton de repli (comme iPadOS).
                                Box(
                                    Modifier
                                        .statusBarsPadding()
                                        .padding(top = Sillon.spacing.l, start = Sillon.spacing.s),
                                ) {
                                    IconButton(onClick = { sidebarVisible = !sidebarVisible }) {
                                        Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Sillon.colors.texteSourdine)
                                    }
                                }
                                Box(Modifier.weight(1f)) { ScreenContent(current) }
                            }
                        }
                        NowPlayingBar(onOpen = { showPlayer = true }, bottomInset = true)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { ScreenContent(current) }
                    NowPlayingBar(onOpen = { showPlayer = true })
                    SillonBottomBar(current) { index = it.ordinal }
                }
            }
        }

        if (showPlayer && playingTrack != null) {
            FullPlayerScreen(onClose = { showPlayer = false })
        }
    }
}

@Composable
private fun ScreenContent(current: SillonDestination) {
    when (current) {
        SillonDestination.ACCUEIL -> AccueilScreen()
        SillonDestination.BIBLIOTHEQUE -> BibliothequeScreen()
        SillonDestination.FAVORIS -> FavorisScreen()
        SillonDestination.RECHERCHE -> RechercheScreen()
        SillonDestination.REGLAGES -> ServerConnectionScreen()
    }
}

/** Barre latérale (grand écran) — repliable. Sélection en cuivre. */
@Composable
private fun SillonRail(current: SillonDestination, onSelect: (SillonDestination) -> Unit) {
    NavigationRail(
        containerColor = Sillon.colors.surfaceElevee,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Spacer(Modifier.height(Sillon.spacing.xl))
        SillonDestination.entries.forEach { dest ->
            NavigationRailItem(
                selected = dest == current,
                onClick = { onSelect(dest) },
                icon = { Icon(dest.icon, contentDescription = str(dest.titleKey)) },
                label = { Text(str(dest.titleKey), style = Sillon.type.corps.copy(fontSize = 12.sp), maxLines = 1) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Sillon.colors.fondNoir,
                    selectedTextColor = Sillon.colors.accentCuivre,
                    indicatorColor = Sillon.colors.accentCuivre,
                    unselectedIconColor = Sillon.colors.texteSourdine,
                    unselectedTextColor = Sillon.colors.texteSourdine,
                ),
            )
        }
    }
}

/** Barre d'onglets du bas (écran étroit). Sélection en cuivre. */
@Composable
private fun SillonBottomBar(current: SillonDestination, onSelect: (SillonDestination) -> Unit) {
    NavigationBar(containerColor = Sillon.colors.surfaceElevee) {
        SillonDestination.entries.forEach { dest ->
            NavigationBarItem(
                selected = dest == current,
                onClick = { onSelect(dest) },
                icon = { Icon(dest.icon, contentDescription = str(dest.titleKey)) },
                label = { Text(str(dest.titleKey), style = Sillon.type.corps.copy(fontSize = 11.sp), maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Sillon.colors.fondNoir,
                    selectedTextColor = Sillon.colors.accentCuivre,
                    indicatorColor = Sillon.colors.accentCuivre,
                    unselectedIconColor = Sillon.colors.texteSourdine,
                    unselectedTextColor = Sillon.colors.texteSourdine,
                ),
            )
        }
    }
}

/** Barre « Lecture en cours » : titre/artiste + lecture/pause ; tap = lecteur plein écran. */
@Composable
private fun NowPlayingBar(onOpen: () -> Unit, bottomInset: Boolean = false) {
    val track by PlayerController.current.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val t = track ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Sillon.colors.surfaceElevee)
            .clickable(onClick = onOpen)
            .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
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
