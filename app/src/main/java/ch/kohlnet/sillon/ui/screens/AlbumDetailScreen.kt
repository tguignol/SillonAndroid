package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import ch.kohlnet.sillon.ui.i18n.S
import ch.kohlnet.sillon.ui.i18n.str
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.data.Album
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.data.Track
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.components.TrackMenuButton
import ch.kohlnet.sillon.ui.components.lazyColumnScrollbar
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.placeholderBrush
import coil3.compose.AsyncImage

/** Détail d'un album : pochette + métadonnées + liste des morceaux (tap = lecture). */
@Composable
fun AlbumDetailScreen(album: Album, onBack: () -> Unit) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(album.id, album.serverId) {
        tracks = MusicRepository.tracks(album)
    }
    val current by PlayerController.current.collectAsState()
    val favorites by MusicRepository.favorites.collectAsState()
    val isFavorite = favorites.any { it.matchKey() == album.matchKey() }
    var showArtist by remember { mutableStateOf(false) }

    if (showArtist && album.artist.isNotBlank()) {
        ArtistDetailScreen(album.artist, onBack = { showArtist = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Sillon.spacing.xl),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = Sillon.colors.texteIvoire,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { MusicRepository.toggleFavorite(album) }) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favori",
                    tint = if (isFavorite) Sillon.colors.accentCuivre else Sillon.colors.texteSourdine,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.l)) {
            AsyncImage(
                model = album.coverUrl,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
                    .background(placeholderBrush(album.title.ifBlank { album.id })),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs)) {
                Text(album.title, style = Sillon.type.display, color = Sillon.colors.texteIvoire)
                if (album.artist.isNotBlank()) {
                    Text(
                        album.artist,
                        style = Sillon.type.corps,
                        color = Sillon.colors.texteSourdine,
                        modifier = Modifier.clickable { showArtist = true },
                    )
                }
            }
        }

        Spacer(Modifier.height(Sillon.spacing.l))

        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .lazyColumnScrollbar(listState, Sillon.colors.texteSourdine),
            verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
        ) {
            // Albums multi-disques : en-tête « Disque N » avant chaque disque. Disque unique : aucun en-tête.
            val multiDisc = tracks.map { it.disc ?: 1 }.distinct().size > 1
            if (multiDisc) {
                tracks.groupBy { it.disc ?: 1 }.toSortedMap().forEach { (disc, discTracks) ->
                    item(key = "disc-$disc") { DiscHeader(disc) }
                    items(discTracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            isPlaying = track.id == current?.id,
                            onClick = { PlayerController.play(tracks, tracks.indexOf(track)) },
                        )
                    }
                }
            } else {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isPlaying = track.id == current?.id,
                        onClick = { PlayerController.play(tracks, tracks.indexOf(track)) },
                    )
                }
            }
        }
    }
}

/** En-tête de disque (albums multi-disques) : « Disque 1 », « Disque 2 », … */
@Composable
private fun DiscHeader(disc: Int) {
    Text(
        text = "${str(S.DISQUE)} $disc",
        style = Sillon.type.displaySmall,
        color = Sillon.colors.texteSourdine,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Sillon.spacing.m, bottom = Sillon.spacing.xs),
    )
}

@Composable
private fun TrackRow(track: Track, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Sillon.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
    ) {
        Text(
            text = track.index?.toString() ?: "•",
            style = Sillon.type.technique,
            color = if (isPlaying) Sillon.colors.accentCuivre else Sillon.colors.texteSourdine,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = track.title,
            style = Sillon.type.corps,
            color = if (isPlaying) Sillon.colors.accentCuivre else Sillon.colors.texteIvoire,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Bouton ⋮ (juste AVANT l'encodage) : menu d'actions du titre (« Ajouter à une playlist » ; les
        // actions de file d'attente y restent code-only, cf. QUEUE_UI_ENABLED). Réutilisable partout.
        TrackMenuButton(track)
        // Type d'encodage (FLAC / ALAC / WAV…) en vert, comme ailleurs.
        track.formatLabel()?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = Sillon.type.technique.copy(fontSize = 10.sp),
                color = Sillon.colors.signalTeal,
                maxLines = 1,
            )
        }
        track.durationMs?.let {
            Text(
                text = formatDuration(it),
                style = Sillon.type.technique,
                color = Sillon.colors.texteSourdine,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
