package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.data.Track
import ch.kohlnet.sillon.player.AudioOutputMonitor
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.components.SpectrumRing
import ch.kohlnet.sillon.ui.components.ThinSlider
import ch.kohlnet.sillon.ui.i18n.S
import ch.kohlnet.sillon.ui.i18n.str
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.placeholderBrush
import coil3.compose.AsyncImage

private enum class PlayerPane { COVER, LYRICS, QUEUE }

/**
 * Lecteur plein écran (façon iOS). ADAPTATIF : étroit (iPhone) = pochette en haut, contrôles dessous ;
 * large (iPad) = deux colonnes. Pochette RONDE entourée d'un spectre. Sous la barre de progression :
 * nom du serveur + qualité (vert). Volume, saut ±10 s, sortie audio.
 */
@Composable
fun FullPlayerScreen(onClose: () -> Unit) {
    val track by PlayerController.current.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    var pane by remember { mutableStateOf(PlayerPane.COVER) }
    LaunchedEffect(Unit) { PlayerController.refreshVolume() } // refléter le volume système courant
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
                horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xxl),
            ) {
                // Gauche : pochette ronde / paroles (la file d'attente est à droite).
                MediaArea(t, if (pane == PlayerPane.QUEUE) PlayerPane.COVER else pane, playing, Modifier.weight(1f).fillMaxHeight())
                // Droite : contrôles EN HAUT + file d'attente DESSOUS (titres suivants/précédents).
                Column(modifier = Modifier.weight(1f)) {
                    Controls(t, playing, position, duration, pane, showQueue = false) { pane = it }
                    Spacer(Modifier.height(Sillon.spacing.l))
                    QueuePanel(Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(Sillon.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MediaArea(t, pane, playing, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(Sillon.spacing.l))
                Controls(t, playing, position, duration, pane) { pane = it }
            }
        }

        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Fermer", tint = Sillon.colors.texteIvoire)
        }
    }
}

@Composable
private fun MediaArea(t: Track, pane: PlayerPane, playing: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (pane) {
            PlayerPane.LYRICS -> LyricsPanel(t, Modifier.fillMaxSize())
            PlayerPane.QUEUE -> QueuePanel(Modifier.fillMaxSize())
            PlayerPane.COVER -> Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                SpectrumRing(
                    playing = playing,
                    color = Sillon.colors.accentCuivre.copy(alpha = 0.55f),
                    accent = Sillon.colors.signalTeal,
                    modifier = Modifier.fillMaxSize(),
                )
                AsyncImage(
                    model = t.coverUrl,
                    contentDescription = t.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize(0.74f)
                        .clip(CircleShape)
                        .background(placeholderBrush(t.title.ifBlank { t.id })),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.Controls(
    t: Track,
    playing: Boolean,
    position: Long,
    duration: Long,
    pane: PlayerPane,
    showQueue: Boolean = true,
    onSetPane: (PlayerPane) -> Unit,
) {
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
    t.album?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = it,
            style = Sillon.type.corps.copy(fontSize = 13.sp),
            color = Sillon.colors.texteSourdine,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(Sillon.spacing.l))

    val dur = duration.coerceAtLeast(1L)
    ThinSlider(
        value = position.coerceIn(0L, dur).toFloat(),
        onValueChange = { PlayerController.seekTo(it.toLong()) },
        valueRange = 0f..dur.toFloat(),
        activeColor = Sillon.colors.accentCuivre,
        inactiveColor = Sillon.colors.texteSourdine.copy(alpha = 0.4f),
        thumbColor = Sillon.colors.accentCuivre,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(position), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
        Text(formatTime(duration), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
    }

    // Provenance (nom du serveur) + qualité — petit, en vert, sous la barre.
    val servers by MusicRepository.servers.collectAsState()
    val serverName = servers.firstOrNull { it.id == t.serverId }?.name
    val info = listOfNotNull(serverName, t.qualityLabel()).joinToString("  ·  ")
    if (info.isNotBlank()) {
        Text(
            text = info,
            style = Sillon.type.technique,
            color = Sillon.colors.signalTeal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(Sillon.spacing.m))

    val shuffle by PlayerController.shuffle.collectAsState()
    val repeatMode by PlayerController.repeatMode.collectAsState()

    // Transport : aléatoire, précédent, −10 s, lecture, +10 s, suivant, répéter.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { PlayerController.toggleShuffle() }) {
            Icon(Icons.Filled.Shuffle, "Aléatoire", tint = tintIf(shuffle), modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = { PlayerController.previous() }) {
            Icon(Icons.Filled.SkipPrevious, "Précédent", tint = Sillon.colors.texteIvoire, modifier = Modifier.size(30.dp))
        }
        IconButton(onClick = { PlayerController.skipBackward() }) {
            Icon(Icons.Filled.Replay10, "−10 s", tint = Sillon.colors.texteIvoire, modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = { PlayerController.togglePlayPause() }) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Lecture",
                tint = Sillon.colors.accentCuivre,
                modifier = Modifier.size(54.dp),
            )
        }
        IconButton(onClick = { PlayerController.skipForward() }) {
            Icon(Icons.Filled.Forward10, "+10 s", tint = Sillon.colors.texteIvoire, modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = { PlayerController.next() }) {
            Icon(Icons.Filled.SkipNext, "Suivant", tint = Sillon.colors.texteIvoire, modifier = Modifier.size(30.dp))
        }
        IconButton(onClick = { PlayerController.cycleRepeat() }) {
            Icon(
                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Répéter",
                tint = tintIf(repeatMode != Player.REPEAT_MODE_OFF),
                modifier = Modifier.size(22.dp),
            )
        }
    }

    Spacer(Modifier.height(Sillon.spacing.m))

    // Volume SOUS le transport (applicatif, façon iOS).
    val volume by PlayerController.volume.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
    ) {
        IconButton(onClick = { PlayerController.adjustVolume(-0.05f) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.VolumeDown, "Moins fort", tint = Sillon.colors.texteSourdine, modifier = Modifier.size(18.dp))
        }
        ThinSlider(
            value = volume,
            onValueChange = { PlayerController.setVolume(it) },
            valueRange = 0f..1f,
            activeColor = Sillon.colors.accentCuivre,
            inactiveColor = Sillon.colors.texteSourdine.copy(alpha = 0.4f),
            thumbColor = Sillon.colors.accentCuivre,
            modifier = Modifier.weight(1f).padding(horizontal = Sillon.spacing.s),
        )
        IconButton(onClick = { PlayerController.adjustVolume(0.05f) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.VolumeUp, "Plus fort", tint = Sillon.colors.texteSourdine, modifier = Modifier.size(18.dp))
        }
    }

    // Espace pour descendre les boutons plus bas.
    Spacer(Modifier.height(Sillon.spacing.xxl))

    // Favori (piste courante) + Paroles + File.
    val favTracks by MusicRepository.favoriteTrackKeys.collectAsState()
    val isFav = t.matchKey() in favTracks
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xl, Alignment.CenterHorizontally),
    ) {
        IconButton(onClick = { MusicRepository.toggleTrackFavorite(t) }) {
            Icon(
                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favori",
                tint = tintIf(isFav),
            )
        }
        IconButton(onClick = { onSetPane(if (pane == PlayerPane.LYRICS) PlayerPane.COVER else PlayerPane.LYRICS) }) {
            Icon(Icons.Filled.Lyrics, "Paroles", tint = tintIf(pane == PlayerPane.LYRICS))
        }
        if (showQueue) {
            IconButton(onClick = { onSetPane(if (pane == PlayerPane.QUEUE) PlayerPane.COVER else PlayerPane.QUEUE) }) {
                Icon(Icons.Filled.QueueMusic, "File d'attente", tint = tintIf(pane == PlayerPane.QUEUE))
            }
        }
    }

    Spacer(Modifier.height(Sillon.spacing.s))
    OutputIndicator()
}

/** Indicateur de sortie audio (Bluetooth / casque / haut-parleur), façon iOS. */
@Composable
private fun OutputIndicator() {
    val output by AudioOutputMonitor.output.collectAsState()
    val icon: ImageVector
    val label: String
    when (output.transport) {
        AudioOutputMonitor.Transport.BLUETOOTH -> {
            icon = Icons.Filled.Bluetooth; label = output.name ?: str(S.OUT_BLUETOOTH)
        }
        AudioOutputMonitor.Transport.WIRED -> {
            icon = Icons.Filled.Headphones; label = str(S.OUT_WIRED)
        }
        else -> {
            icon = Icons.Filled.Speaker; label = str(S.OUT_SPEAKER)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Sillon.colors.texteSourdine, modifier = Modifier.size(16.dp))
        Text(label, style = Sillon.type.technique, color = Sillon.colors.texteSourdine, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun tintIf(active: Boolean) =
    if (active) Sillon.colors.accentCuivre else Sillon.colors.texteSourdine

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
