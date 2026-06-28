package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.data.Album
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.ui.components.SourceBadge
import ch.kohlnet.sillon.ui.i18n.S
import ch.kohlnet.sillon.ui.i18n.str
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.placeholderBrush
import coil3.compose.AsyncImage
import kotlin.math.abs

private val CARD = 150.dp

/**
 * Accueil — sections en CARROUSELS horizontaux (façon iOS `HomeView`) : Albums récents, Albums
 * préférés, Albums aléatoires, Redécouvrir. Les sections aléatoires se RE-MÉLANGENT au surscroll
 * (tirer le carrousel au-delà d'un bord), avec retour haptique.
 */
@Composable
fun AccueilScreen() {
    val albums by MusicRepository.albums.collectAsState()
    val favorites by MusicRepository.favorites.collectAsState()
    val loading by MusicRepository.loading.collectAsState()
    var selected by remember { mutableStateOf<Album?>(null) }
    val scrollState = rememberScrollState() // hissé → la position survit à l'aller-retour vers un album

    val sel = selected
    if (sel != null) {
        AlbumDetailScreen(sel, onBack = { selected = null })
        return
    }

    // Tirages aléatoires ; réinitialisés quand la bibliothèque change (ex : (dés)activation serveur).
    var aleatoires by remember(albums) { mutableStateOf(albums.shuffled().take(15)) }
    var redecouvrir by remember(albums) { mutableStateOf(albums.shuffled().take(15)) }
    val onClick: (Album) -> Unit = { selected = it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(top = Sillon.spacing.l, bottom = Sillon.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xl),
    ) {
        Text(
            text = str(S.ACCUEIL),
            style = Sillon.type.display,
            color = Sillon.colors.texteIvoire,
            modifier = Modifier.padding(horizontal = Sillon.spacing.xl),
        )

        if (albums.isEmpty()) {
            if (loading) LoadingHint() else EmptyHint(str(S.BIBLIOTHEQUE_VIDE))
        } else {
            Section(str(S.ALBUMS_RECENTS)) { AlbumCarousel(albums.take(30), onClick) }
            if (favorites.isNotEmpty()) {
                Section(str(S.ALBUMS_PREFERES)) { AlbumCarousel(favorites, onClick) }
            }
            Section(str(S.ALBUMS_ALEATOIRES)) {
                AlbumCarousel(aleatoires, onClick, onReshuffle = { aleatoires = albums.shuffled().take(15) })
            }
            Section(str(S.REDECOUVRIR)) {
                AlbumCarousel(redecouvrir, onClick, onReshuffle = { redecouvrir = albums.shuffled().take(15) })
            }
        }
    }
}

@Composable
fun BibliothequeScreen() {
    val albums by MusicRepository.albums.collectAsState()
    val loading by MusicRepository.loading.collectAsState()
    AlbumGridScreen(str(S.BIBLIOTHEQUE), albums, str(S.BIBLIOTHEQUE_VIDE), loading)
}

@Composable
fun FavorisScreen() {
    val favorites by MusicRepository.favorites.collectAsState()
    AlbumGridScreen(str(S.FAVORIS), favorites, str(S.AUCUN_FAVORI), loading = false)
}

/** En-tête de section + contenu (carrousel). */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Sillon.spacing.m)) {
        Text(
            text = title,
            style = Sillon.type.displaySmall,
            color = Sillon.colors.texteSourdine,
            modifier = Modifier.padding(horizontal = Sillon.spacing.xl),
        )
        content()
    }
}

/**
 * Carrousel d'albums horizontal. Si `onReshuffle` non nul, un surscroll au-delà d'un bord (> 70 dp)
 * déclenche un re-mélange + un retour haptique, puis se réarme une fois revenu dans les limites.
 */
@Composable
private fun AlbumCarousel(albums: List<Album>, onClick: (Album) -> Unit, onReshuffle: (() -> Unit)? = null) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thresholdPx = with(density) { 70.dp.toPx() }
    val resetPx = with(density) { 20.dp.toPx() }
    var overscroll by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(true) }

    val connection = remember(onReshuffle, thresholdPx, resetPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (onReshuffle == null) return Offset.Zero
                if (available.x != 0f) {
                    overscroll += available.x
                    if (armed && abs(overscroll) > thresholdPx) {
                        armed = false
                        onReshuffle()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                } else if (consumed.x != 0f && abs(overscroll) < resetPx) {
                    overscroll = 0f
                    armed = true
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                overscroll = 0f
                armed = true
                return Velocity.Zero
            }
        }
    }

    LazyRow(
        modifier = if (onReshuffle != null) Modifier.nestedScroll(connection) else Modifier,
        contentPadding = PaddingValues(horizontal = Sillon.spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
    ) {
        lazyRowItems(albums, key = { it.id }) { album ->
            AlbumCard(album, Modifier.width(CARD)) { onClick(album) }
        }
    }
}

@Composable
private fun AlbumGridScreen(title: String, albums: List<Album>, emptyText: String, loading: Boolean) {
    var selected by remember { mutableStateOf<Album?>(null) }
    val gridState = rememberLazyGridState() // hissé → la position survit à l'ouverture d'un album
    val sel = selected
    if (sel != null) {
        AlbumDetailScreen(sel, onBack = { selected = null })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Sillon.spacing.xl)
            .padding(top = Sillon.spacing.l),
    ) {
        Text(text = title, style = Sillon.type.display, color = Sillon.colors.texteIvoire)
        if (albums.isEmpty()) {
            if (loading) LoadingHint() else EmptyHint(emptyText)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(CARD),
                state = gridState,
                modifier = Modifier.padding(top = Sillon.spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
                verticalArrangement = Arrangement.spacedBy(Sillon.spacing.l),
                contentPadding = PaddingValues(bottom = Sillon.spacing.xxl),
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumCard(album) { selected = album }
                }
            }
        }
    }
}

/** Grille d'albums réutilisable (Recherche…). */
@Composable
fun AlbumGrid(albums: List<Album>, modifier: Modifier = Modifier, onClick: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CARD),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.l),
        contentPadding = PaddingValues(bottom = Sillon.spacing.xxl),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album) { onClick(album) }
        }
    }
}

/** Carte d'album : pochette + badge source en ICÔNE (bas-droite, si >1 serveur actif) + titre/artiste. */
@Composable
private fun AlbumCard(album: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val servers by MusicRepository.servers.collectAsState()
    val activeCount = servers.count { it.active }
    val sourceIds = album.sources.ifEmpty { listOf(album.serverId) }
    val sourceTypes = sourceIds.mapNotNull { id -> servers.firstOrNull { it.id == id }?.type }
    val showBadge = activeCount > 1 && sourceTypes.isNotEmpty()

    Column(
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = album.coverUrl,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
                    .background(placeholderBrush(album.title.ifBlank { album.id })),
            )
            if (showBadge) {
                SourceBadge(
                    types = sourceTypes.distinct(),
                    sourceCount = sourceIds.size,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Sillon.spacing.xs),
                )
            }
        }
        Text(
            text = album.title,
            style = Sillon.type.corps,
            color = Sillon.colors.texteIvoire,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (album.artist.isNotBlank()) {
            Text(
                text = album.artist,
                style = Sillon.type.corps.copy(fontSize = 13.sp),
                color = Sillon.colors.texteSourdine,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoadingHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Sillon.spacing.xl, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.l),
    ) {
        CircularProgressIndicator(color = Sillon.colors.accentCuivre)
        Text(str(S.CHARGEMENT), style = Sillon.type.corps, color = Sillon.colors.texteSourdine, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Sillon.spacing.xl, vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = Sillon.type.corps, color = Sillon.colors.texteSourdine, textAlign = TextAlign.Center)
    }
}
