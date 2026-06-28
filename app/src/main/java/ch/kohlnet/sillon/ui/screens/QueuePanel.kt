package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.data.Track
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.components.lazyColumnScrollbar
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.placeholderBrush
import coil3.compose.AsyncImage

/** Mode du panneau latéral : titres de l'ALBUM (défaut) ou la FILE D'ATTENTE complète. */
private enum class QueueMode { ALBUM, QUEUE }

/**
 * Panneau latéral du lecteur. Bascule en haut :
 *  - « Album » (DÉFAUT) : les titres de l'album du morceau courant, en ordre de piste.
 *  - « File d'attente » : la file de lecture complète (ordre de lecture).
 * Le morceau EN COURS est en cuivre ; tap = saut. Barre de défilement fine (sans index alphabétique).
 * Défile automatiquement vers le morceau courant.
 */
@Composable
fun QueuePanel(modifier: Modifier = Modifier) {
    val queue by PlayerController.queue.collectAsState()
    val current by PlayerController.current.collectAsState()
    var qmode by rememberSaveable { mutableStateOf(QueueMode.ALBUM) }
    val listState = rememberLazyListState()

    // Titres de l'album du morceau courant, en ordre de piste.
    val albumList = remember(queue, current) {
        val alb = current?.album
        val list = if (alb.isNullOrBlank()) queue else queue.filter { it.album == alb }
        list.sortedBy { it.index ?: Int.MAX_VALUE }
    }
    // La file d'attente est-elle IDENTIQUE à l'album en cours (mêmes titres, même ordre) ?
    // Si oui, le bouton « File d'attente » n'apporte rien → on le masque et on force le mode Album.
    val sameAsAlbum = remember(queue, albumList) {
        queue.map { "${it.serverId}/${it.id}" } == albumList.map { "${it.serverId}/${it.id}" }
    }
    LaunchedEffect(sameAsAlbum) { if (sameAsAlbum) qmode = QueueMode.ALBUM }

    val items = remember(queue, albumList, qmode) {
        when (qmode) {
            QueueMode.ALBUM -> albumList
            QueueMode.QUEUE -> queue
        }
    }

    val currentIndex = items.indexOfFirst { it.id == current?.id && it.serverId == current?.serverId }
    LaunchedEffect(currentIndex, qmode) {
        if (currentIndex >= 0) runCatching { listState.animateScrollToItem(currentIndex) }
    }

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Sillon.spacing.s, vertical = Sillon.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
        ) {
            QueueChip("Album", qmode == QueueMode.ALBUM) { qmode = QueueMode.ALBUM }
            // Bouton « File d'attente » seulement si la file DIFFÈRE de l'album en cours.
            if (!sameAsAlbum) {
                QueueChip("File d'attente", qmode == QueueMode.QUEUE) { qmode = QueueMode.QUEUE }
            }
        }
        if (qmode == QueueMode.ALBUM) {
            current?.album?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = Sillon.type.corps.copy(fontSize = 13.sp),
                    color = Sillon.colors.texteSourdine,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Sillon.spacing.s, vertical = Sillon.spacing.xs),
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).lazyColumnScrollbar(listState, Sillon.colors.texteSourdine),
            contentPadding = PaddingValues(horizontal = Sillon.spacing.s, vertical = Sillon.spacing.xs),
        ) {
            itemsIndexed(items, key = { _, t -> t.serverId + "/" + t.id }) { _, track ->
                val isCurrent = track.id == current?.id && track.serverId == current?.serverId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val qi = queue.indexOfFirst { it.id == track.id && it.serverId == track.serverId }
                            if (qi >= 0) PlayerController.playIndex(qi)
                        }
                        .padding(vertical = Sillon.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
                ) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(Sillon.spacing.xs))
                            .background(placeholderBrush(track.title.ifBlank { track.id })),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = Sillon.type.corps,
                            color = if (isCurrent) Sillon.colors.accentCuivre else Sillon.colors.texteIvoire,
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
                    track.durationMs?.let {
                        Text(
                            queueDuration(it),
                            style = Sillon.type.technique,
                            color = if (isCurrent) Sillon.colors.accentCuivre else Sillon.colors.texteSourdine,
                        )
                    }
                }
            }
        }
    }
}

/** Pastille de bascule du panneau (Album / File d'attente). */
@Composable
private fun QueueChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = Sillon.type.corps,
        color = if (selected) Sillon.colors.accentCuivre else Sillon.colors.texteSourdine,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Sillon.colors.surfaceElevee else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = Sillon.spacing.m, vertical = Sillon.spacing.xs),
    )
}

private fun queueDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
