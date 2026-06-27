package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.data.Album
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.placeholderBrush
import coil3.compose.AsyncImage

/** Accueil + Bibliothèque : même grille d'albums (réutilisée), tirée du serveur connecté. */
private const val EMPTY_LIBRARY = "Aucun album.\nConnecte-toi à un serveur dans Réglages."

@Composable
fun AccueilScreen() {
    val albums by MusicRepository.albums.collectAsState()
    AlbumGridScreen("Accueil", "Albums récents", albums, EMPTY_LIBRARY)
}

@Composable
fun BibliothequeScreen() {
    val albums by MusicRepository.albums.collectAsState()
    AlbumGridScreen("Bibliothèque", null, albums, EMPTY_LIBRARY)
}

@Composable
fun FavorisScreen() {
    val favorites by MusicRepository.favorites.collectAsState()
    AlbumGridScreen("Favoris", null, favorites, "Aucun favori.\nTouche le cœur sur un album.")
}

@Composable
private fun AlbumGridScreen(title: String, sectionLabel: String?, albums: List<Album>, emptyText: String) {
    var selected by remember { mutableStateOf<Album?>(null) }
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
        Text(
            text = title,
            style = Sillon.type.display,
            color = Sillon.colors.texteIvoire,
        )
        Spacer(Modifier.height(Sillon.spacing.m))

        if (albums.isEmpty()) {
            EmptyHint(emptyText)
        } else {
            if (sectionLabel != null) {
                Text(
                    text = sectionLabel,
                    style = Sillon.type.displaySmall,
                    color = Sillon.colors.texteSourdine,
                )
                Spacer(Modifier.height(Sillon.spacing.m))
            }
            AlbumGrid(albums) { selected = it }
        }
    }
}

/** Grille d'albums réutilisable (Accueil, Bibliothèque, Favoris, Recherche). */
@Composable
fun AlbumGrid(albums: List<Album>, modifier: Modifier = Modifier, onClick: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.l),
        contentPadding = PaddingValues(bottom = Sillon.spacing.xxl),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album, onClick = { onClick(album) })
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    val servers by MusicRepository.servers.collectAsState()
    val sourceTypes = album.sources.ifEmpty { listOf(album.serverId) }
        .mapNotNull { sid -> servers.firstOrNull { it.id == sid }?.type }
        .distinct()
    val showBadge = servers.size > 1 && sourceTypes.isNotEmpty()
    val badgeText = if (sourceTypes.size == 1) sourceTypes[0].badge else "${album.sources.size} serveurs"

    Column(
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
        modifier = Modifier.clickable(onClick = onClick),
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
                Text(
                    text = badgeText,
                    style = Sillon.type.technique,
                    color = Sillon.colors.texteIvoire,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Sillon.spacing.xs)
                        .clip(RoundedCornerShape(50))
                        .background(Sillon.colors.fondNoir.copy(alpha = 0.7f))
                        .padding(horizontal = Sillon.spacing.xs, vertical = 2.dp),
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
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = Sillon.type.corps,
            color = Sillon.colors.texteSourdine,
            textAlign = TextAlign.Center,
        )
    }
}
