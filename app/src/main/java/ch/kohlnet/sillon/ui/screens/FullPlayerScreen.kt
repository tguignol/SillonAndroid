package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.kohlnet.sillon.data.Track
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.placeholderBrush
import coil3.compose.AsyncImage

/**
 * Lecteur plein écran (façon iOS). ADAPTATIF :
 * - écran étroit (téléphone / Fold plié → iPhone) : pochette en haut, contrôles dessous.
 * - écran large (Fold déplié / tablette → iPad) : DEUX COLONNES — pochette à gauche, contrôles à droite.
 */
@Composable
fun FullPlayerScreen(onClose: () -> Unit) {
    val track by PlayerController.current.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val t = track ?: return

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Sillon.colors.fondNoir)
            .safeDrawingPadding(),
    ) {
        val wide = maxWidth >= 600.dp

        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(Sillon.spacing.xxl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xxl),
            ) {
                Cover(t, Modifier.weight(1f).aspectRatio(1f))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Controls(t, playing, position, duration)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(Sillon.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Cover(t, Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(Sillon.spacing.xl))
                Controls(t, playing, position, duration)
                Spacer(Modifier.weight(1f))
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Fermer", tint = Sillon.colors.texteIvoire)
        }
    }
}

@Composable
private fun Cover(t: Track, modifier: Modifier) {
    AsyncImage(
        model = t.coverUrl,
        contentDescription = t.title,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
            .background(placeholderBrush(t.title.ifBlank { t.id })),
    )
}

@Composable
private fun ColumnScope.Controls(t: Track, playing: Boolean, position: Long, duration: Long) {
    Text(
        text = t.title,
        style = Sillon.type.display,
        color = Sillon.colors.texteIvoire,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
    if (t.artist.isNotBlank()) {
        Text(
            text = t.artist,
            style = Sillon.type.corps,
            color = Sillon.colors.texteSourdine,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(Sillon.spacing.l))

    val dur = duration.coerceAtLeast(1L)
    Slider(
        value = position.coerceIn(0L, dur).toFloat(),
        onValueChange = { PlayerController.seekTo(it.toLong()) },
        valueRange = 0f..dur.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = Sillon.colors.accentCuivre,
            activeTrackColor = Sillon.colors.accentCuivre,
            inactiveTrackColor = Sillon.colors.texteSourdine,
        ),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(position), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
        Text(formatTime(duration), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
    }

    Spacer(Modifier.height(Sillon.spacing.l))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xl, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { PlayerController.previous() }) {
            Icon(Icons.Filled.SkipPrevious, "Précédent", tint = Sillon.colors.texteIvoire, modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { PlayerController.togglePlayPause() }) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Lecture",
                tint = Sillon.colors.accentCuivre,
                modifier = Modifier.size(56.dp),
            )
        }
        IconButton(onClick = { PlayerController.next() }) {
            Icon(Icons.Filled.SkipNext, "Suivant", tint = Sillon.colors.texteIvoire, modifier = Modifier.size(36.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
