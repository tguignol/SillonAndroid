package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.theme.Sillon

/** File d'attente : morceaux en cours/à venir ; morceau courant en cuivre ; tap = saut. */
@Composable
fun QueuePanel(modifier: Modifier = Modifier) {
    val queue by PlayerController.queue.collectAsState()
    val current by PlayerController.current.collectAsState()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = Sillon.spacing.m, horizontal = Sillon.spacing.s),
    ) {
        itemsIndexed(queue) { i, track ->
            val isCurrent = track.id == current?.id && track.serverId == current?.serverId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { PlayerController.playIndex(i) }
                    .padding(vertical = Sillon.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
            ) {
                Text(
                    text = "${i + 1}",
                    style = Sillon.type.technique,
                    color = if (isCurrent) Sillon.colors.accentCuivre else Sillon.colors.texteSourdine,
                    modifier = Modifier.width(28.dp),
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
            }
        }
    }
}
