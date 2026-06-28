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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.i18n.S
import ch.kohlnet.sillon.ui.i18n.str
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.placeholderBrush
import coil3.compose.AsyncImage

/**
 * File d'attente (façon iOS) : en-tête « File d'attente » + bouton « Vider » ; chaque morceau avec
 * pochette, titre/artiste, durée. Morceau courant en cuivre ; tap = saut.
 */
@Composable
fun QueuePanel(modifier: Modifier = Modifier) {
    val queue by PlayerController.queue.collectAsState()
    val current by PlayerController.current.collectAsState()

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Sillon.spacing.s, vertical = Sillon.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                str(S.FILE_ATTENTE),
                style = Sillon.type.displaySmall,
                color = Sillon.colors.texteSourdine,
                modifier = Modifier.weight(1f),
            )
            if (queue.isNotEmpty()) {
                TextButton(onClick = { PlayerController.clearQueue() }) {
                    Text(str(S.VIDER), style = Sillon.type.corps, color = Sillon.colors.accentCuivre)
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = Sillon.spacing.s, vertical = Sillon.spacing.xs)) {
            itemsIndexed(queue, key = { _, t -> t.id }) { i, track ->
                val isCurrent = track.id == current?.id && track.serverId == current?.serverId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { PlayerController.playIndex(i) }
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
                        Text(queueDuration(it), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
                    }
                }
            }
        }
    }
}

private fun queueDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
