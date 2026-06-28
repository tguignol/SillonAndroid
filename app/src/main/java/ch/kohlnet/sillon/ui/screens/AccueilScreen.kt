package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import ch.kohlnet.sillon.data.ServerType
import ch.kohlnet.sillon.ui.components.AzScrollIndex
import ch.kohlnet.sillon.ui.components.ServerMark
import ch.kohlnet.sillon.ui.components.SourceBadge
import ch.kohlnet.sillon.ui.components.azSortKey
import ch.kohlnet.sillon.ui.components.azTargetIndex
import ch.kohlnet.sillon.ui.components.indexLetter
import ch.kohlnet.sillon.ui.components.lazyGridScrollbar
import ch.kohlnet.sillon.ui.i18n.S
import ch.kohlnet.sillon.ui.i18n.str
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.placeholderBrush
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

private enum class LibraryMode { ALBUMS, ARTISTS }

/** Bibliothèque : bascule Albums / Artistes, tri alphabétique, index A-Z à droite (presse M → albums en M). */
@Composable
fun BibliothequeScreen() {
    val albums by MusicRepository.albums.collectAsState()
    val loading by MusicRepository.loading.collectAsState()
    val servers by MusicRepository.servers.collectAsState()
    var mode by rememberSaveable { mutableStateOf(LibraryMode.ALBUMS) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()   // hissés → survivent à l'ouverture d'un détail
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    selectedAlbum?.let {
        AlbumDetailScreen(it, onBack = { selectedAlbum = null }); return
    }
    selectedArtist?.let {
        ArtistDetailScreen(it, onBack = { selectedArtist = null }); return
    }

    val sortedAlbums = remember(albums) { albums.sortedBy { azSortKey(it.title) } }
    val artists = remember(albums, servers) {
        albums.filter { it.artist.isNotBlank() }
            .groupBy { it.artist.trim().lowercase() }
            .map { (_, list) ->
                val name = list.first().artist.trim()
                val ids = list.flatMap { a -> a.sources.ifEmpty { listOf(a.serverId) } }.distinct()
                val types = ids.mapNotNull { id -> servers.firstOrNull { it.id == id }?.type }.distinct()
                name to types
            }
            .sortedBy { azSortKey(it.first) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Sillon.spacing.xl)
            .padding(top = Sillon.spacing.l),
    ) {
        Text(text = str(S.BIBLIOTHEQUE), style = Sillon.type.display, color = Sillon.colors.texteIvoire)
        Spacer(Modifier.height(Sillon.spacing.m))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == LibraryMode.ALBUMS,
                onClick = { mode = LibraryMode.ALBUMS },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(str(S.ALBUMS), style = Sillon.type.corps) }
            SegmentedButton(
                selected = mode == LibraryMode.ARTISTS,
                onClick = { mode = LibraryMode.ARTISTS },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(str(S.ARTISTES), style = Sillon.type.corps) }
        }
        Spacer(Modifier.height(Sillon.spacing.m))

        if (albums.isEmpty()) {
            if (loading) LoadingHint() else EmptyHint(str(S.BIBLIOTHEQUE_VIDE))
        } else when (mode) {
            LibraryMode.ALBUMS -> IndexedAlbumGrid(sortedAlbums, gridState, scope) { selectedAlbum = it }
            LibraryMode.ARTISTS -> IndexedArtistList(artists, listState, scope) { selectedArtist = it }
        }
    }
}

@Composable
private fun IndexedAlbumGrid(
    albums: List<Album>,
    gridState: LazyGridState,
    scope: CoroutineScope,
    onClick: (Album) -> Unit,
) {
    val letters = remember(albums) { albums.map { indexLetter(it.title) } }
    val present = remember(letters) { letters.toSet() }
    val current by remember(letters) {
        derivedStateOf { letters.getOrNull(gridState.firstVisibleItemIndex) ?: 'A' }
    }
    Row(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CARD),
            state = gridState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
            verticalArrangement = Arrangement.spacedBy(Sillon.spacing.l),
            contentPadding = PaddingValues(bottom = Sillon.spacing.xxl),
        ) {
            items(albums, key = { it.id }) { album -> AlbumCard(album) { onClick(album) } }
        }
        AzScrollIndex(present = present, current = current, onLetter = { c ->
            scope.launch { gridState.scrollToItem(azTargetIndex(letters, c)) }
        })
    }
}

@Composable
private fun IndexedArtistList(
    artists: List<Pair<String, List<ServerType>>>,
    listState: LazyListState,
    scope: CoroutineScope,
    onClick: (String) -> Unit,
) {
    val letters = remember(artists) { artists.map { indexLetter(it.first) } }
    val present = remember(letters) { letters.toSet() }
    val current by remember(letters) {
        derivedStateOf { letters.getOrNull(listState.firstVisibleItemIndex) ?: 'A' }
    }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Sillon.spacing.xxl),
        ) {
            lazyRowItems(artists, key = { it.first }) { (name, types) ->
                ArtistRow(name, types) { onClick(name) }
            }
        }
        AzScrollIndex(present = present, current = current, onLetter = { c ->
            scope.launch { listState.scrollToItem(azTargetIndex(letters, c)) }
        })
    }
}

@Composable
private fun ArtistRow(name: String, types: List<ServerType>, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Sillon.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
    ) {
        Text(name, style = Sillon.type.corps, color = Sillon.colors.texteIvoire, modifier = Modifier.weight(1f))
        // Icône(s) du/des serveur(s) d'origine, à la place du nombre.
        types.forEach { t -> ServerMark(t, Modifier.size(18.dp)) }
    }
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

    val connection = remember(onReshuffle, thresholdPx) {
        object : NestedScrollConnection {
            var overscroll = 0f
            var peak = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // On accumule seulement le débordement (au bord) pendant le glissement, SANS rien déclencher.
                if (onReshuffle != null && available.x != 0f) {
                    overscroll += available.x
                    peak = maxOf(peak, abs(overscroll))
                }
                return Offset.Zero
            }

            // Re-mélange AU LÂCHÉ du doigt (fin du geste), pas pendant le glissement → moins abrupt.
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (onReshuffle != null && peak > thresholdPx) {
                    onReshuffle()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                overscroll = 0f
                peak = 0f
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
                modifier = Modifier
                    .padding(top = Sillon.spacing.m)
                    .lazyGridScrollbar(gridState, Sillon.colors.texteSourdine),
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
