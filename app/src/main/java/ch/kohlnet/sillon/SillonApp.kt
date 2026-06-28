package ch.kohlnet.sillon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.components.ThinSlider
import ch.kohlnet.sillon.ui.theme.placeholderBrush
import coil3.compose.AsyncImage
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
        // Destinations principales en haut ; Réglages poussé TOUT EN BAS (les paramètres sont
        // habituellement en bas).
        SillonDestination.entries.filter { it != SillonDestination.REGLAGES }.forEach { dest ->
            RailItem(dest, current, onSelect)
        }
        Spacer(Modifier.weight(1f))
        RailItem(SillonDestination.REGLAGES, current, onSelect)
        Spacer(Modifier.height(Sillon.spacing.l))
    }
}

@Composable
private fun RailItem(dest: SillonDestination, current: SillonDestination, onSelect: (SillonDestination) -> Unit) {
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

/**
 * Barre « Lecture en cours » riche (façon mini-lecteur) : pochette + cœur + titre/artiste centrés +
 * barre de progression (temps écoulé / restant) + précédent / lecture-pause (rond) / suivant.
 * Tap (hors contrôles) = lecteur plein écran.
 */
@Composable
private fun NowPlayingBar(onOpen: () -> Unit, bottomInset: Boolean = false) {
    val track by PlayerController.current.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val favTracks by MusicRepository.favoriteTrackKeys.collectAsState()
    val t = track ?: return
    val isFav = t.matchKey() in favTracks
    val dur = duration.coerceAtLeast(1L)

    val progress = @Composable { modifier: Modifier ->
        ThinSlider(
            value = position.coerceIn(0L, dur).toFloat(),
            onValueChange = { PlayerController.seekTo(it.toLong()) },
            valueRange = 0f..dur.toFloat(),
            activeColor = Sillon.colors.accentCuivre,
            inactiveColor = Sillon.colors.texteSourdine.copy(alpha = 0.4f),
            thumbColor = Sillon.colors.accentCuivre,
            modifier = modifier,
        )
    }
    val playButton = @Composable {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Sillon.colors.texteIvoire)
                .clickable { PlayerController.togglePlayPause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Lecture",
                tint = Sillon.colors.fondNoir,
                modifier = Modifier.size(26.dp),
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
            .padding(horizontal = Sillon.spacing.m, vertical = Sillon.spacing.xs),
    ) {
        // Écran étroit (Fold replié / téléphone) → version COMPACTE : pochette + titre/artiste +
        // lecture/suivant, fine ligne de progression en bas. Écran large → version riche.
        val compact = maxWidth < 560.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Sillon.spacing.l))
                .background(Sillon.colors.surfaceElevee)
                .clickable(onClick = onOpen),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Sillon.spacing.m, vertical = Sillon.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
            ) {
                AsyncImage(
                    model = t.coverUrl,
                    contentDescription = t.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (compact) 40.dp else 48.dp)
                        .clip(RoundedCornerShape(Sillon.spacing.s))
                        .background(placeholderBrush(t.title.ifBlank { t.id })),
                )
                if (!compact) {
                    IconButton(onClick = { MusicRepository.toggleTrackFavorite(t) }) {
                        Icon(
                            if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (isFav) Sillon.colors.accentCuivre else Sillon.colors.texteSourdine,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    Text(
                        t.title,
                        style = Sillon.type.corps,
                        color = Sillon.colors.texteIvoire,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (compact) TextAlign.Start else TextAlign.Center,
                    )
                    val sub = listOfNotNull(t.artist.takeIf { it.isNotBlank() }, t.album?.takeIf { it.isNotBlank() })
                    if (sub.isNotEmpty()) {
                        Text(
                            sub.joinToString(" · "),
                            style = Sillon.type.corps.copy(fontSize = 12.sp),
                            color = Sillon.colors.texteSourdine,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = if (compact) TextAlign.Start else TextAlign.Center,
                        )
                    }
                    if (!compact) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(barTime(position), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
                            progress(Modifier.weight(1f).padding(horizontal = Sillon.spacing.s))
                            Text("-" + barTime(dur - position), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
                        }
                    }
                }

                if (!compact) {
                    IconButton(onClick = { PlayerController.previous() }) {
                        Icon(Icons.Filled.SkipPrevious, "Précédent", tint = Sillon.colors.texteIvoire)
                    }
                }
                playButton()
                IconButton(onClick = { PlayerController.next() }) {
                    Icon(Icons.Filled.SkipNext, "Suivant", tint = Sillon.colors.texteIvoire)
                }
            }
            // Écran étroit : progression pleine largeur en bas (sans labels de temps, pour gagner de la place).
            if (compact) {
                progress(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Sillon.spacing.m)
                        .padding(bottom = Sillon.spacing.xs),
                )
            }
        }
    }
}

private fun barTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
