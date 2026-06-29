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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed as lazyRowItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import ch.kohlnet.sillon.data.Playlists
import ch.kohlnet.sillon.data.ServerPlaylist
import ch.kohlnet.sillon.data.PlayHistory
import ch.kohlnet.sillon.data.ServerType
import ch.kohlnet.sillon.data.Track
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.components.AzScrollIndex
import ch.kohlnet.sillon.ui.components.ServerMark
import ch.kohlnet.sillon.ui.components.SourceBadge
import ch.kohlnet.sillon.ui.components.TrackMenuButton
import ch.kohlnet.sillon.ui.components.sillonSegmentedColors
import ch.kohlnet.sillon.ui.components.azSortKey
import ch.kohlnet.sillon.ui.components.azTargetIndex
import ch.kohlnet.sillon.ui.components.indexLetter
import ch.kohlnet.sillon.ui.components.lazyColumnScrollbar
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

/** Normalise un libellé pour la correspondance (minuscules, espaces compactés). */
private fun normKey(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ")

/**
 * Accueil — sections en CARROUSELS horizontaux (façon iOS `HomeView`) : Albums récents, Albums
 * préférés, Albums aléatoires, Redécouvrir. Les sections aléatoires se RE-MÉLANGENT au surscroll
 * (tirer le carrousel au-delà d'un bord), avec retour haptique.
 */
@Composable
fun AccueilScreen() {
    val albums by MusicRepository.albums.collectAsState()
    val favorites by MusicRepository.visibleFavorites.collectAsState()
    val loading by MusicRepository.loading.collectAsState()
    val stats by PlayHistory.stats.collectAsState()
    val servers by MusicRepository.servers.collectAsState()
    val favTrackKeys by MusicRepository.favoriteTrackKeys.collectAsState()
    val playlists by Playlists.playlists.collectAsState()
    var selected by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var showArtists by remember { mutableStateOf(false) }
    var seeAll by remember { mutableStateOf<SeeAll?>(null) }
    val scrollState = rememberScrollState() // hissé → la position survit à l'aller-retour vers un album
    val seeAllGridState = rememberLazyGridState() // hissé → la grille « Voir tout » garde sa position au retour

    // Artistes (pour l'accès rapide « Artistes »), même calcul que la Bibliothèque.
    val artists = remember(albums, servers) {
        albums.filter { it.artist.isNotBlank() }
            .groupBy { it.artist.trim().lowercase() }
            .map { (_, list) ->
                val name = list.first().artist.trim()
                val ids = list.flatMap { a -> a.sources.ifEmpty { listOf(a.serverId) } }.distinct()
                val types = ids.mapNotNull { id -> servers.firstOrNull { it.id == id }?.type }.distinct()
                ArtistEntry(name, types, list.first())
            }
            .sortedBy { azSortKey(it.name) }
    }

    val sel = selected
    if (sel != null) {
        AlbumDetailScreen(sel, onBack = { selected = null })
        return
    }
    selectedArtist?.let {
        ArtistDetailScreen(it, onBack = { selectedArtist = null }); return
    }
    if (showArtists) {
        ArtistsListInline(artists, onBack = { showArtists = false }) { selectedArtist = it }
        return
    }
    selectedPlaylist?.let {
        PlaylistDetailScreen(it, onBack = { selectedPlaylist = null }); return
    }
    // « Voir tout » d'une section → vue plein écran type bibliothèque (l'album ouvert prime, cf. plus haut).
    val sa = seeAll
    if (sa != null) {
        when (sa) {
            is SeeAll.Albums -> SeeAllAlbumsScreen(sa.title, sa.items, seeAllGridState, alphabetical = sa.alphabetical, onBack = { seeAll = null }) { selected = it }
            is SeeAll.Tracks -> SeeAllTracksScreen(sa.title, sa.items, onBack = { seeAll = null })
        }
        return
    }

    // Tirages aléatoires ; réinitialisés quand la bibliothèque change (ex : (dés)activation serveur).
    var aleatoires by remember(albums) { mutableStateOf(albums.shuffled().take(15)) }
    var redecouvrir by remember(albums) { mutableStateOf(albums.shuffled().take(15)) }
    val onClick: (Album) -> Unit = { selected = it }

    // Sections « écoutes » dérivées de l'historique, filtrées aux serveurs ACTIFS (listes COMPLÈTES ;
    // les carrousels en prennent un sous-ensemble, le « voir tout » affiche tout).
    val mostPlayedStats = remember(stats, servers) {
        val activeIds = servers.filter { it.active }.map { it.id }.toSet()
        stats.filter { it.count > 0 && it.serverId in activeIds }.sortedByDescending { it.count }
    }
    val mostPlayedTracks = remember(mostPlayedStats) { mostPlayedStats.map { it.toTrack() } }
    val mostPlayedCounts = remember(mostPlayedStats) { mostPlayedStats.map { it.count } }
    // Albums reliés à l'historique → résolus dans la bibliothèque ACTIVE par TITRE normalisé (l'artiste
    // des pistes peut manquer côté serveur) ; donc déjà filtrés par serveur actif.
    val albumsByTitle = remember(albums) { albums.groupBy { normKey(it.title) } }
    val recentlyPlayedAlbums = remember(stats, albumsByTitle) {
        stats.filter { it.album.isNotBlank() }
            .groupBy { normKey(it.album) }
            .mapNotNull { (k, v) -> albumsByTitle[k]?.firstOrNull()?.let { it to v.maxOf { s -> s.lastPlayedAt } } }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    val mostPlayedAlbums = remember(stats, albumsByTitle) {
        stats.filter { it.album.isNotBlank() }
            .groupBy { normKey(it.album) }
            .mapNotNull { (k, v) -> albumsByTitle[k]?.firstOrNull()?.let { it to v.sumOf { s -> s.count } } }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    // Pistes préférées = pistes mises en favori (par titre+artiste) ayant un instantané d'écoute, serveurs actifs.
    val favoriteTracks = remember(stats, favTrackKeys, servers) {
        val activeIds = servers.filter { it.active }.map { it.id }.toSet()
        stats.filter { it.matchKey() in favTrackKeys && it.serverId in activeIds }
            .sortedByDescending { it.lastPlayedAt }
            .map { it.toTrack() }
    }

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
            // Accès rapides (façon iOS) : Albums, Artistes, Mixer les favoris (lecture aléatoire des pistes favorites).
            val albumsTitle = str(S.ALBUMS)
            // « Albums » → grille de TOUS les albums, alphabétique avec index A-Z (comme l'onglet Albums
            // de la Bibliothèque), et non plus la barre fine en ordre naturel.
            QuickActions(
                favoriteTracks = favoriteTracks,
                onAlbums = { seeAll = SeeAll.Albums(albumsTitle, albums, alphabetical = true) },
                onArtists = { showArtists = true },
            )
            val tRecents = str(S.ALBUMS_RECENTS)
            Section(tRecents, onSeeAll = { seeAll = SeeAll.Albums(tRecents, albums) }) {
                AlbumCarousel(albums.take(30), onClick)
            }
            if (mostPlayedTracks.isNotEmpty()) {
                val t = str(S.TITRES_PLUS_ECOUTES)
                Section(t, onSeeAll = { seeAll = SeeAll.Tracks(t, mostPlayedTracks) }) {
                    // Mise en page « 2 par hauteur, vignette à gauche (×1,5), titre à côté ».
                    // Ancienne version (carrousel simple), gardée au cas où — il suffit de la remettre :
                    // TrackCarousel(mostPlayedTracks.take(15)) { i -> PlayerController.play(mostPlayedTracks, i) }
                    TrackGridCarousel(mostPlayedTracks.take(20), mostPlayedCounts.take(20)) { i -> PlayerController.play(mostPlayedTracks, i) }
                }
            }
            if (recentlyPlayedAlbums.isNotEmpty()) {
                val t = str(S.ALBUMS_RECEMMENT)
                Section(t, onSeeAll = { seeAll = SeeAll.Albums(t, recentlyPlayedAlbums) }) {
                    AlbumCarousel(recentlyPlayedAlbums.take(15), onClick)
                }
            }
            if (mostPlayedAlbums.isNotEmpty()) {
                val t = str(S.PLUS_ECOUTES)
                Section(t, onSeeAll = { seeAll = SeeAll.Albums(t, mostPlayedAlbums) }) {
                    AlbumCarousel(mostPlayedAlbums.take(15), onClick)
                }
            }
            if (favoriteTracks.isNotEmpty()) {
                val t = str(S.PISTES_PREFEREES)
                Section(t, onSeeAll = { seeAll = SeeAll.Tracks(t, favoriteTracks) }) {
                    TrackCarousel(favoriteTracks.take(15)) { i -> PlayerController.play(favoriteTracks, i) }
                }
            }
            if (favorites.isNotEmpty()) {
                val t = str(S.ALBUMS_PREFERES)
                Section(t, onSeeAll = { seeAll = SeeAll.Albums(t, favorites) }) {
                    AlbumCarousel(favorites, onClick)
                }
            }
            Section(str(S.ALBUMS_ALEATOIRES)) {
                AlbumCarousel(aleatoires, onClick, onReshuffle = { aleatoires = albums.shuffled().take(15) })
            }
            Section(str(S.REDECOUVRIR)) {
                AlbumCarousel(redecouvrir, onClick, onReshuffle = { redecouvrir = albums.shuffled().take(15) })
            }
            // Playlists en fin d'accueil (façon iOS HomeView).
            if (playlists.isNotEmpty()) {
                Section(str(S.PLAYLISTS)) {
                    PlaylistCarousel(playlists.take(12)) { selectedPlaylist = it }
                }
            }
        }
    }
}

/** Carrousel de playlists (carte carrée avec icône) — section « Playlists » de l'accueil. */
@Composable
private fun PlaylistCarousel(playlists: List<ch.kohlnet.sillon.data.Playlist>, onOpen: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Sillon.spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
    ) {
        lazyRowItems(playlists, key = { it.id }) { pl ->
            Column(
                verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
                modifier = Modifier.width(CARD).clickable { onOpen(pl.id) },
            ) {
                Box(
                    modifier = Modifier
                        .size(CARD)
                        .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
                        .background(Sillon.colors.surfaceElevee),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = Sillon.colors.accentCuivre, modifier = Modifier.size(44.dp))
                }
                Text(pl.name, style = Sillon.type.corps, color = Sillon.colors.texteIvoire, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private enum class LibraryMode { RECENTS, ALBUMS, ARTISTS, TITRES, PLAYLISTS }

/** Entrée artiste : nom, serveurs d'origine, et un album représentatif (pour déduire le format). */
private data class ArtistEntry(val name: String, val types: List<ServerType>, val sample: Album)

/** Bibliothèque : bascule Albums / Artistes, tri alphabétique, index A-Z à droite (presse M → albums en M). */
@Composable
fun BibliothequeScreen() {
    val albums by MusicRepository.albums.collectAsState()
    val loading by MusicRepository.loading.collectAsState()
    val servers by MusicRepository.servers.collectAsState()
    var mode by rememberSaveable { mutableStateOf(LibraryMode.ALBUMS) }
    var ascending by rememberSaveable { mutableStateOf(true) } // tri A→Z (vrai) / Z→A
    var browse by remember { mutableStateOf(false) } // « Parcourir » (genre / décennie)
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var selectedServerPlaylist by remember { mutableStateOf<ServerPlaylist?>(null) }
    val gridState = rememberLazyGridState()   // hissés → survivent à l'ouverture d'un détail
    val recentsGridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val titresListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    selectedAlbum?.let {
        AlbumDetailScreen(it, onBack = { selectedAlbum = null }); return
    }
    selectedArtist?.let {
        ArtistDetailScreen(it, onBack = { selectedArtist = null }); return
    }
    selectedPlaylist?.let {
        PlaylistDetailScreen(it, onBack = { selectedPlaylist = null }); return
    }
    selectedServerPlaylist?.let {
        ServerPlaylistDetailScreen(it, onBack = { selectedServerPlaylist = null }); return
    }
    if (browse) {
        BrowseScreen(onBack = { browse = false }); return
    }

    val sortedAlbums = remember(albums, ascending) {
        val s = albums.sortedBy { azSortKey(it.title) }
        if (ascending) s else s.reversed()
    }
    val artists = remember(albums, servers, ascending) {
        val base = albums.filter { it.artist.isNotBlank() }
            .groupBy { it.artist.trim().lowercase() }
            .map { (_, list) ->
                val name = list.first().artist.trim()
                val ids = list.flatMap { a -> a.sources.ifEmpty { listOf(a.serverId) } }.distinct()
                val types = ids.mapNotNull { id -> servers.firstOrNull { it.id == id }?.type }.distinct()
                ArtistEntry(name, types, list.first())
            }
            .sortedBy { azSortKey(it.name) }
        if (ascending) base else base.reversed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Sillon.spacing.xl)
            .padding(top = Sillon.spacing.l),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = str(S.BIBLIOTHEQUE), style = Sillon.type.display, color = Sillon.colors.texteIvoire)
            Spacer(Modifier.weight(1f))
            // « Parcourir » (genre/décennie) et le tri A→Z ne concernent que les albums/artistes.
            if (mode != LibraryMode.PLAYLISTS) {
                IconButton(onClick = { browse = true }) {
                    Icon(Icons.Filled.Category, contentDescription = "Parcourir (genre / décennie)", tint = Sillon.colors.texteSourdine)
                }
                SortDirectionButtons(ascending) { ascending = it }
            }
        }
        Spacer(Modifier.height(Sillon.spacing.m))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val libModes = listOf(
                LibraryMode.RECENTS to str(S.RECENTS),
                LibraryMode.ALBUMS to str(S.ALBUMS),
                LibraryMode.ARTISTS to str(S.ARTISTES),
                LibraryMode.TITRES to str(S.TITRES_LIB),
                LibraryMode.PLAYLISTS to str(S.PLAYLISTS),
            )
            libModes.forEachIndexed { i, (m, label) ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { mode = m },
                    shape = SegmentedButtonDefaults.itemShape(i, libModes.size),
                    colors = sillonSegmentedColors(),
                    icon = {},
                ) { Text(label, style = Sillon.type.corps.copy(fontSize = 13.sp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
            }
        }
        Spacer(Modifier.height(Sillon.spacing.m))

        // Playlists et Titres sont chargés indépendamment des albums serveur (affichés même biblio vide).
        if (mode == LibraryMode.PLAYLISTS) {
            PlaylistsListScreen(onOpenLocal = { selectedPlaylist = it }, onOpenServer = { selectedServerPlaylist = it })
        } else if (mode == LibraryMode.TITRES) {
            IndexedTracksList(titresListState, scope)
        } else if (albums.isEmpty()) {
            if (loading) LoadingHint() else EmptyHint(str(S.BIBLIOTHEQUE_VIDE))
        } else when (mode) {
            // Récents = albums dans leur ordre NATUREL (les plus récents d'abord), sans tri alpha ni index A-Z.
            LibraryMode.RECENTS -> AlbumGrid(albums, Modifier.fillMaxSize(), recentsGridState) { selectedAlbum = it }
            LibraryMode.ALBUMS -> IndexedAlbumGrid(sortedAlbums, gridState, scope) { selectedAlbum = it }
            LibraryMode.ARTISTS -> IndexedArtistList(artists, listState, scope) { selectedArtist = it }
            LibraryMode.TITRES -> {}
            LibraryMode.PLAYLISTS -> {}
        }
    }
}

/** Bascule de tri alphabétique : A→Z (ascendant) / Z→A (descendant). */
@Composable
private fun SortToggle(ascending: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Sillon.colors.surfaceElevee)
            .clickable(onClick = onToggle)
            .padding(horizontal = Sillon.spacing.m, vertical = Sillon.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Sort,
            contentDescription = null,
            tint = Sillon.colors.texteIvoire,
            modifier = Modifier.size(16.dp),
        )
        Text(
            if (ascending) "A→Z" else "Z→A",
            style = Sillon.type.technique,
            color = Sillon.colors.texteIvoire,
        )
    }
}

/**
 * Tri alphabétique : UNE seule icône « ↑↓ » qui BASCULE le sens à chaque tap (A→Z ⇄ Z→A),
 * comme l'exemple demandé. Pastille cuivre sur fond surélevé.
 */
@Composable
private fun SortDirectionButtons(ascending: Boolean, onSet: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Sillon.colors.surfaceElevee)
            .clickable { onSet(!ascending) }
            .padding(Sillon.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.SwapVert,
            contentDescription = if (ascending) "Tri A→Z — taper pour Z→A" else "Tri Z→A — taper pour A→Z",
            tint = Sillon.colors.accentCuivre,
            modifier = Modifier.size(26.dp),
        )
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
    artists: List<ArtistEntry>,
    listState: LazyListState,
    scope: CoroutineScope,
    onClick: (String) -> Unit,
) {
    val letters = remember(artists) { artists.map { indexLetter(it.name) } }
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
            lazyRowItems(artists, key = { it.name }) { entry ->
                ArtistRow(entry) { onClick(entry.name) }
            }
        }
        AzScrollIndex(present = present, current = current, onLetter = { c ->
            scope.launch { listState.scrollToItem(azTargetIndex(letters, c)) }
        })
    }
}

/** Onglet « Titres » : TOUS les titres de la bibliothèque, A-Z, index latéral. Tap = lecture depuis ce titre. */
@Composable
private fun IndexedTracksList(listState: LazyListState, scope: CoroutineScope) {
    val tracks by MusicRepository.allTracks.collectAsState()
    val loadingTracks by MusicRepository.loadingTracks.collectAsState()
    LaunchedEffect(Unit) { MusicRepository.loadAllTracks() }
    val sorted = remember(tracks) { tracks.sortedBy { azSortKey(it.title) } }

    if (sorted.isEmpty()) {
        if (loadingTracks) LoadingHint() else EmptyHint(str(S.BIBLIOTHEQUE_VIDE))
        return
    }
    val letters = remember(sorted) { sorted.map { indexLetter(it.title) } }
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
            lazyRowItemsIndexed(sorted, key = { _, t -> t.serverId + "/" + t.id }) { i, track ->
                TrackListRow(track) { PlayerController.play(sorted, i) }
            }
        }
        AzScrollIndex(present = present, current = current, onLetter = { c ->
            scope.launch { listState.scrollToItem(azTargetIndex(letters, c)) }
        })
    }
}

/** Ligne de l'onglet « Titres » : pochette + titre/artiste + icône de provenance + ⋮ + durée. */
@Composable
private fun TrackListRow(track: Track, onClick: () -> Unit) {
    val servers by MusicRepository.servers.collectAsState()
    val type = servers.firstOrNull { it.id == track.serverId }?.type
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Sillon.spacing.s, horizontal = Sillon.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
    ) {
        AsyncImage(
            model = track.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(Sillon.spacing.xs)).background(placeholderBrush(track.title.ifBlank { track.id })),
        )
        Column(Modifier.weight(1f)) {
            Text(track.title, style = Sillon.type.corps, color = Sillon.colors.texteIvoire, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.artist.isNotBlank()) {
                Text(track.artist, style = Sillon.type.corps.copy(fontSize = 13.sp), color = Sillon.colors.texteSourdine, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (type != null) ServerMark(type, Modifier.size(14.dp))
        TrackMenuButton(track)
        track.durationMs?.let { Text(seeAllDuration(it), style = Sillon.type.technique, color = Sillon.colors.texteSourdine) }
    }
}

@Composable
private fun ArtistRow(entry: ArtistEntry, onClick: () -> Unit) {
    // Format audio (FLAC/ALAC/WAV…) récupéré à la volée pour l'album représentatif (caché côté repo).
    var format by remember(entry.sample.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.sample.id) { format = MusicRepository.albumFormat(entry.sample) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Sillon.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.name,
            style = Sillon.type.corps,
            color = Sillon.colors.texteIvoire,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Format : colonne de largeur fixe, aligné à droite → cohérent d'une ligne à l'autre.
        Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) {
            format?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = Sillon.type.technique.copy(fontSize = 10.sp),
                    color = Sillon.colors.signalTeal,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(Sillon.spacing.s))
        // Icône(s) serveur : colonne de largeur fixe, alignées à droite, plus petites.
        Row(
            modifier = Modifier.width(36.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entry.types.forEach { t -> ServerMark(t, Modifier.size(14.dp)) }
        }
    }
}

private enum class FavMode { ALBUMS, TRACKS, PLAYLISTS }

@Composable
fun FavorisScreen() {
    val favorites by MusicRepository.visibleFavorites.collectAsState()
    val stats by PlayHistory.stats.collectAsState()
    val favTrackKeys by MusicRepository.favoriteTrackKeys.collectAsState()
    val servers by MusicRepository.servers.collectAsState()
    // Titres favoris : pistes mises en favori ayant un instantané d'écoute, sur serveurs actifs (idem Accueil).
    val favoriteTracks = remember(stats, favTrackKeys, servers) {
        val activeIds = servers.filter { it.active }.map { it.id }.toSet()
        stats.filter { it.matchKey() in favTrackKeys && it.serverId in activeIds }
            .sortedByDescending { it.lastPlayedAt }
            .map { it.toTrack() }
    }

    var mode by rememberSaveable { mutableStateOf(FavMode.ALBUMS) }
    var selected by remember { mutableStateOf<Album?>(null) }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var selectedServerPlaylist by remember { mutableStateOf<ServerPlaylist?>(null) }
    // États de défilement HISSÉS (avant les early-return) → la position survit à l'ouverture d'un détail
    // et l'utilisateur revient là où il avait cliqué.
    val albumsGridState = rememberLazyGridState()
    val tracksListState = rememberLazyListState()
    val sel = selected
    if (sel != null) {
        AlbumDetailScreen(sel, onBack = { selected = null })
        return
    }
    selectedPlaylist?.let {
        PlaylistDetailScreen(it, onBack = { selectedPlaylist = null }); return
    }
    selectedServerPlaylist?.let {
        ServerPlaylistDetailScreen(it, onBack = { selectedServerPlaylist = null }); return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Sillon.spacing.xl)
            .padding(top = Sillon.spacing.l),
    ) {
        Text(str(S.FAVORIS), style = Sillon.type.display, color = Sillon.colors.texteIvoire)
        Spacer(Modifier.height(Sillon.spacing.m))
        // Trois boutons : Albums préférés / Pistes préférées / Playlists (favorites).
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == FavMode.ALBUMS,
                onClick = { mode = FavMode.ALBUMS },
                shape = SegmentedButtonDefaults.itemShape(0, 3),
                colors = sillonSegmentedColors(),
                icon = {},
            ) {
                Text(str(S.ALBUMS_PREFERES), style = Sillon.type.corps.copy(fontSize = 14.sp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            SegmentedButton(
                selected = mode == FavMode.TRACKS,
                onClick = { mode = FavMode.TRACKS },
                shape = SegmentedButtonDefaults.itemShape(1, 3),
                colors = sillonSegmentedColors(),
                icon = {},
            ) {
                Text(str(S.PISTES_PREFEREES), style = Sillon.type.corps.copy(fontSize = 14.sp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            SegmentedButton(
                selected = mode == FavMode.PLAYLISTS,
                onClick = { mode = FavMode.PLAYLISTS },
                shape = SegmentedButtonDefaults.itemShape(2, 3),
                colors = sillonSegmentedColors(),
                icon = {},
            ) {
                Text(str(S.PLAYLISTS), style = Sillon.type.corps.copy(fontSize = 14.sp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(Sillon.spacing.m))

        when (mode) {
            FavMode.ALBUMS ->
                if (favorites.isEmpty()) {
                    EmptyHint(str(S.AUCUN_FAVORI))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(CARD),
                        state = albumsGridState,
                        modifier = Modifier.fillMaxSize().lazyGridScrollbar(albumsGridState, Sillon.colors.texteSourdine),
                        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.l),
                        contentPadding = PaddingValues(bottom = Sillon.spacing.xxl),
                    ) {
                        items(favorites, key = { it.id }) { album ->
                            AlbumCard(album) { selected = album }
                        }
                    }
                }
            FavMode.TRACKS ->
                if (favoriteTracks.isEmpty()) {
                    EmptyHint(str(S.AUCUN_FAVORI))
                } else {
                    LazyColumn(
                        state = tracksListState,
                        modifier = Modifier.fillMaxSize().lazyColumnScrollbar(tracksListState, Sillon.colors.texteSourdine),
                        contentPadding = PaddingValues(bottom = Sillon.spacing.xxl),
                    ) {
                        lazyRowItemsIndexed(favoriteTracks, key = { _, t -> t.serverId + "/" + t.id }) { i, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { PlayerController.play(favoriteTracks, i) }
                                    .padding(vertical = Sillon.spacing.s),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
                            ) {
                                AsyncImage(
                                    model = track.coverUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(Sillon.spacing.xs))
                                        .background(placeholderBrush(track.title.ifBlank { track.id })),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, style = Sillon.type.corps, color = Sillon.colors.texteIvoire, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (track.artist.isNotBlank()) {
                                        Text(track.artist, style = Sillon.type.corps.copy(fontSize = 13.sp), color = Sillon.colors.texteSourdine, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                track.formatLabel()?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = Sillon.type.technique.copy(fontSize = 10.sp), color = Sillon.colors.signalTeal, maxLines = 1)
                                }
                                track.durationMs?.let {
                                    Text(seeAllDuration(it), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
                                }
                            }
                        }
                    }
                }
            FavMode.PLAYLISTS ->
                FavoritePlaylistsList(
                    onOpenLocal = { selectedPlaylist = it },
                    onOpenServer = { selectedServerPlaylist = it },
                )
        }
    }
}

/** En-tête de section + contenu (carrousel). `onSeeAll` non nul → flèche « voir tout » à droite du titre. */
@Composable
private fun Section(title: String, onSeeAll: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Sillon.spacing.m)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Sillon.spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = Sillon.type.displaySmall,
                color = Sillon.colors.texteSourdine,
                modifier = Modifier.weight(1f),
            )
            if (onSeeAll != null) {
                IconButton(onClick = onSeeAll, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Voir tout",
                        tint = Sillon.colors.texteSourdine,
                    )
                }
            }
        }
        content()
    }
}

/** Rangée d'accès rapides en haut de l'Accueil (façon iOS) : Albums, Artistes, Mixer les favoris. */
@Composable
private fun QuickActions(favoriteTracks: List<Track>, onAlbums: () -> Unit, onArtists: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Sillon.spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
    ) {
        QuickActionCard(str(S.ALBUMS), Icons.Filled.Album, Modifier.weight(1f), onClick = onAlbums)
        QuickActionCard(str(S.ARTISTES), Icons.Filled.Mic, Modifier.weight(1f), onClick = onArtists)
        // Mixer les favoris = lecture ALÉATOIRE des pistes favorites ; désactivé si aucune.
        QuickActionCard(
            str(S.MIXER_FAVORIS), Icons.Filled.Shuffle, Modifier.weight(1f),
            enabled = favoriteTracks.isNotEmpty(),
        ) { if (favoriteTracks.isNotEmpty()) PlayerController.play(favoriteTracks.shuffled(), 0) }
    }
}

/** Carte d'accès rapide (icône teal + libellé) sur fond surélevé, façon iOS `QuickAction`. */
@Composable
private fun QuickActionCard(label: String, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
            .background(Sillon.colors.surfaceElevee)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Sillon.colors.signalTeal else Sillon.colors.texteSourdine, modifier = Modifier.size(22.dp))
        Text(label, style = Sillon.type.technique, color = if (enabled) Sillon.colors.texteIvoire else Sillon.colors.texteSourdine, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Liste d'artistes plein écran (accès rapide « Artistes » de l'Accueil), avec index A-Z. */
@Composable
private fun ArtistsListInline(artists: List<ArtistEntry>, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = Sillon.spacing.xl).padding(top = Sillon.spacing.l),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Sillon.colors.texteIvoire)
            }
            Text(str(S.ARTISTES), style = Sillon.type.display, color = Sillon.colors.texteIvoire)
        }
        Spacer(Modifier.height(Sillon.spacing.m))
        if (artists.isEmpty()) EmptyHint(str(S.BIBLIOTHEQUE_VIDE)) else IndexedArtistList(artists, listState, scope) { onOpen(it) }
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

/** Carrousel horizontal de pistes (les plus écoutées). Tap = lecture de la liste à partir de l'index. */
@Composable
private fun TrackCarousel(tracks: List<Track>, onPlay: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Sillon.spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
    ) {
        lazyRowItemsIndexed(tracks, key = { _, t -> t.serverId + "/" + t.id }) { i, track ->
            TrackCard(track, Modifier.width(CARD)) { onPlay(i) }
        }
    }
}

/**
 * Carrousel des titres en GRILLE HORIZONTALE : 2 titres empilés par colonne, vignette carrée À GAUCHE
 * (≈ la moitié de la hauteur de section) avec titre+artiste à côté ; chaque cellule fait 1,5× la largeur
 * d'une vignette normale. MÊME hauteur que [TrackCarousel]. `counts` = nb de lectures (façon « plus
 * écoutés »), optionnel. Tap = lecture de la liste à partir de l'index.
 */
@Composable
private fun TrackGridCarousel(tracks: List<Track>, counts: List<Int>? = null, onPlay: (Int) -> Unit) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(2),
        modifier = Modifier.height(CARD + 46.dp), // ≈ même hauteur que le carrousel simple
        contentPadding = PaddingValues(horizontal = Sillon.spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
    ) {
        itemsIndexed(tracks, key = { _, t -> t.serverId + "/" + t.id }) { i, track ->
            TrackGridRow(track, counts?.getOrNull(i)) { onPlay(i) }
        }
    }
}

/** Une cellule de [TrackGridCarousel] : vignette à gauche + titre/artiste (et nb de lectures) à côté. */
@Composable
private fun TrackGridRow(track: Track, count: Int?, onClick: () -> Unit) {
    val servers by MusicRepository.servers.collectAsState()
    val type = servers.firstOrNull { it.id == track.serverId }?.type
    Row(
        Modifier.width(CARD * 1.5f).fillMaxHeight().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
    ) {
        AsyncImage(
            model = track.coverUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
                .background(placeholderBrush(track.title.ifBlank { track.id })),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(track.title, style = Sillon.type.corps, color = Sillon.colors.texteIvoire, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.artist.isNotBlank()) {
                Text(track.artist, style = Sillon.type.corps.copy(fontSize = 13.sp), color = Sillon.colors.texteSourdine, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Mini-icône de PROVENANCE (Jellyfin / Navidrome) + nb de lectures.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (type != null) ServerMark(type, Modifier.size(12.dp))
                if (count != null) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Sillon.colors.texteSourdine, modifier = Modifier.size(13.dp))
                    Text("$count", style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
                }
            }
        }
    }
}

/** Carte de piste (pochette + titre + artiste), façon iOS `TrackCard`. */
@Composable
private fun TrackCard(track: Track, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = track.coverUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
                .background(placeholderBrush(track.title.ifBlank { track.id })),
        )
        Text(
            text = track.title,
            style = Sillon.type.corps,
            color = Sillon.colors.texteIvoire,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (track.artist.isNotBlank()) {
            Text(
                text = track.artist,
                style = Sillon.type.corps.copy(fontSize = 13.sp),
                color = Sillon.colors.texteSourdine,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
fun AlbumGrid(albums: List<Album>, modifier: Modifier = Modifier, state: LazyGridState = rememberLazyGridState(), onClick: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CARD),
        state = state,
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
    // Sources ACTIVES de CET album (la dédup peut le placer sur plusieurs serveurs).
    val activeSourceIds = (album.sources.ifEmpty { listOf(album.serverId) })
        .filter { id -> servers.any { it.id == id && it.active } }
        .distinct()
    val sourceTypes = activeSourceIds.mapNotNull { id -> servers.firstOrNull { it.id == id }?.type }.distinct()
    // Badge UNIQUEMENT si l'album est réellement sur PLUSIEURS sources actives.
    // Source unique (ex. Jellyfin seul, Navidrome désactivé) → PAS d'icône.
    val showBadge = activeSourceIds.size > 1

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
                    types = sourceTypes,
                    sourceCount = activeSourceIds.size,
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

/** Cible d'un « voir tout » de section : une grille d'albums ou une liste de titres. */
private sealed interface SeeAll {
    val title: String
    data class Albums(override val title: String, val items: List<Album>, val alphabetical: Boolean = false) : SeeAll
    data class Tracks(override val title: String, val items: List<Track>) : SeeAll
}

/** En-tête « retour + titre » des vues plein écran « voir tout ». */
@Composable
private fun SeeAllHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Sillon.colors.texteIvoire)
        }
        Text(
            title,
            style = Sillon.type.display,
            color = Sillon.colors.texteIvoire,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** « Voir tout » → grille d'albums (même design que la bibliothèque), dans l'ordre de la section. */
@Composable
private fun SeeAllAlbumsScreen(title: String, albums: List<Album>, gridState: LazyGridState, alphabetical: Boolean = false, onBack: () -> Unit, onClick: (Album) -> Unit) {
    val scope = rememberCoroutineScope()
    // « alphabetical » : tri A→Z + index alphabétique latéral (ex. action rapide « Albums »).
    // Sinon : ordre de la section (récents, plus écoutés…) + barre de défilement fine.
    val shown = remember(albums, alphabetical) {
        if (alphabetical) albums.sortedBy { azSortKey(it.title) } else albums
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Sillon.spacing.xl)
            .padding(top = Sillon.spacing.l),
    ) {
        SeeAllHeader(title, onBack)
        Spacer(Modifier.height(Sillon.spacing.m))
        if (alphabetical) {
            IndexedAlbumGrid(shown, gridState, scope, onClick)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(CARD),
                state = gridState,
                modifier = Modifier.fillMaxSize().lazyGridScrollbar(gridState, Sillon.colors.texteSourdine),
                horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
                verticalArrangement = Arrangement.spacedBy(Sillon.spacing.l),
                contentPadding = PaddingValues(bottom = Sillon.spacing.xxl),
            ) {
                items(shown, key = { it.id }) { album -> AlbumCard(album) { onClick(album) } }
            }
        }
    }
}

/** « Voir tout » → liste de titres (pochette + titre + artiste + format + durée), tap = lecture. */
@Composable
private fun SeeAllTracksScreen(title: String, tracks: List<Track>, onBack: () -> Unit) {
    val listState = rememberLazyListState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Sillon.spacing.xl)
            .padding(top = Sillon.spacing.l),
    ) {
        SeeAllHeader(title, onBack)
        Spacer(Modifier.height(Sillon.spacing.m))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().lazyColumnScrollbar(listState, Sillon.colors.texteSourdine),
            contentPadding = PaddingValues(bottom = Sillon.spacing.xxl),
        ) {
            lazyRowItemsIndexed(tracks, key = { _, t -> t.serverId + "/" + t.id }) { i, track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { PlayerController.play(tracks, i) }
                        .padding(vertical = Sillon.spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
                ) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(Sillon.spacing.xs))
                            .background(placeholderBrush(track.title.ifBlank { track.id })),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = Sillon.type.corps,
                            color = Sillon.colors.texteIvoire,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (track.artist.isNotBlank()) {
                            Text(
                                track.artist,
                                style = Sillon.type.corps.copy(fontSize = 13.sp),
                                color = Sillon.colors.texteSourdine,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    track.formatLabel()?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = Sillon.type.technique.copy(fontSize = 10.sp), color = Sillon.colors.signalTeal, maxLines = 1)
                    }
                    track.durationMs?.let {
                        Text(seeAllDuration(it), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
                    }
                }
            }
        }
    }
}

private fun seeAllDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
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
