package ch.kohlnet.sillon.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.data.Track
import ch.kohlnet.sillon.data.SpectrumPrefs
import ch.kohlnet.sillon.data.SpectrumStyle
import ch.kohlnet.sillon.player.AudioOutputMonitor
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.components.SpectrumRing
import ch.kohlnet.sillon.ui.components.ThinSlider
import ch.kohlnet.sillon.ui.i18n.S
import ch.kohlnet.sillon.ui.i18n.str
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.placeholderBrush
import coil3.compose.AsyncImage

private enum class PlayerPane { COVER, LYRICS, QUEUE, EQUALIZER }

/**
 * État d'UI du lecteur conservé HORS composition → survit à la fermeture/réouverture du lecteur (T6) :
 * on retrouve la vue (pochette / paroles / égaliseur / file) telle qu'on l'a laissée.
 */
private object PlayerUiState {
    val paneState = mutableStateOf(PlayerPane.COVER)
    val queueFileState = mutableStateOf(false)
}

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
    // Pane et mode file PERSISTANTS (singleton hors composition) → survivent à la fermeture/réouverture (T6).
    var pane by PlayerUiState.paneState
    var queueFile by PlayerUiState.queueFileState
    LaunchedEffect(Unit) { PlayerController.refreshVolume() } // refléter le volume système courant
    val t = track ?: return

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Sillon.colors.fondNoir)
            // SWIPE vers le BAS (zones non défilables : pochette, espaces) = abaisser le lecteur (T4).
            .pointerInput(Unit) {
                var total = 0f
                val threshold = 120.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > threshold) onClose() },
                ) { change, dy -> total += dy; change.consume() }
            }
            // Calque PLEIN ÉCRAN : consommer TOUS les taps, sinon un tap dans une zone vide traverse vers
            // l'écran derrière (ex. ouvrait le menu ⋮ d'une rangée du détail d'album).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding(),
    ) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(Sillon.spacing.xxl),
                horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xxl),
            ) {
                // Gauche : pochette ronde / paroles (la file d'attente est à droite).
                MediaArea(t, if (pane == PlayerPane.QUEUE) PlayerPane.COVER else pane, playing, wide = true, Modifier.weight(1f).fillMaxHeight())
                // Droite : contrôles EN HAUT + file d'attente DESSOUS (titres suivants/précédents).
                Column(modifier = Modifier.weight(1f)) {
                    // tight = true : colonne ~moitié de l'écran interne → transport compacté pour que
                    // les 7 boutons (dont « répéter ») tiennent sans être coupés.
                    Controls(t, playing, position, duration, pane, showQueue = false, tight = true, queueFile = queueFile, onQueueFileChange = { queueFile = it }) { pane = it }
                    Spacer(Modifier.height(Sillon.spacing.l))
                    QueuePanel(Modifier.weight(1f).fillMaxWidth(), file = queueFile, onFileChange = { queueFile = it })
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(Sillon.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MediaArea(t, pane, playing, wide = false, Modifier.fillMaxWidth().weight(1f), file = queueFile, onFileChange = { queueFile = it })
                // Écran externe : titre / artiste / album RÉPARTIS dans l'espace entre le bas du spectre et
                // la barre (au lieu d'être tassés en bas) — bloc remonté, espace partagé.
                Column(
                    modifier = Modifier.fillMaxWidth().weight(0.5f),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TrackMeta(t)
                }
                Controls(t, playing, position, duration, pane, queueFile = queueFile, onQueueFileChange = { queueFile = it }, showMeta = false) { pane = it }
            }
        }

        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).size(48.dp)) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Fermer",
                tint = Sillon.colors.texteIvoire,
                modifier = Modifier.size(34.dp),
            )
        }
        SleepTimerButton(Modifier.align(Alignment.TopEnd))
    }
}

/** Bouton minuterie de sommeil (en haut à droite du lecteur) : 15/30/45/60 min, fin du morceau, désactiver. */
@Composable
private fun SleepTimerButton(modifier: Modifier = Modifier) {
    val end by PlayerController.sleepTimerEndMs.collectAsState()
    val active = end != null
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Filled.Bedtime,
                contentDescription = "Minuterie de sommeil",
                tint = if (active) Sillon.colors.accentCuivre else Sillon.colors.texteIvoire,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(15, 30, 45, 60).forEach { m ->
                DropdownMenuItem(
                    text = { Text("$m min", style = Sillon.type.corps) },
                    onClick = { PlayerController.setSleepTimer(m); open = false },
                )
            }
            DropdownMenuItem(
                text = { Text("Fin du morceau", style = Sillon.type.corps) },
                onClick = { PlayerController.setSleepTimerEndOfTrack(); open = false },
            )
            if (active) {
                DropdownMenuItem(
                    text = { Text("Désactiver", style = Sillon.type.corps, color = Sillon.colors.accentCuivre) },
                    onClick = { PlayerController.cancelSleepTimer(); open = false },
                )
            }
        }
    }
}

@Composable
private fun MediaArea(t: Track, pane: PlayerPane, playing: Boolean, wide: Boolean, modifier: Modifier, file: Boolean = false, onFileChange: (Boolean) -> Unit = {}) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (pane) {
            PlayerPane.LYRICS ->
                if (wide) {
                    // Écran intérieur : vignette (plus petite) EN HAUT + paroles dessous, ligne en cours centrée,
                    // bouton « Traduire » gardé en haut des paroles.
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CoverThumb(t, Modifier.fillMaxWidth(0.42f).padding(top = Sillon.spacing.m))
                        Spacer(Modifier.height(Sillon.spacing.m))
                        LyricsPanel(t, Modifier.fillMaxWidth().weight(1f))
                    }
                } else {
                    LyricsPanel(t, Modifier.fillMaxSize())
                }
            PlayerPane.QUEUE -> QueuePanel(Modifier.fillMaxSize(), file = file, onFileChange = onFileChange)
            PlayerPane.EQUALIZER -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                EqualizerPanel()
            }
            PlayerPane.COVER -> {
                val style by SpectrumPrefs.style.collectAsState()
                val square = style == SpectrumStyle.OFF_SQUARE
                val coverFrac = if (style == SpectrumStyle.OFF || square) 0.92f else 0.74f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable { SpectrumPrefs.cycle() }, // taper la pochette = changer de visualiseur
                    contentAlignment = Alignment.Center,
                ) {
                    SpectrumRing(
                        playing = playing,
                        style = style,
                        color = Sillon.colors.accentCuivre,
                        accent = Sillon.colors.signalTeal,
                        modifier = Modifier.fillMaxSize(),
                    )
                    AsyncImage(
                        model = t.coverUrl,
                        contentDescription = t.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize(coverFrac)
                            .clip(if (square) RoundedCornerShape(Sillon.spacing.cardCorner) else CircleShape)
                            .background(placeholderBrush(t.title.ifBlank { t.id })),
                    )
                }
            }
        }
    }
}

/** Petite pochette carrée arrondie (vue paroles sur écran large) : façon Apple Music. */
@Composable
private fun CoverThumb(t: Track, modifier: Modifier = Modifier) {
    AsyncImage(
        model = t.coverUrl,
        contentDescription = t.title,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
            .background(placeholderBrush(t.title.ifBlank { t.id })),
    )
}

/**
 * Bouton de transport à zone tactile SERRÉE (≈ icône + petit anneau), au lieu du minimum 48 dp d'IconButton.
 * Évite les « avance/recule » accidentels quand on tape un peu à côté de l'icône.
 */
@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(iconSize + 8.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Titre de la chanson + artiste + album, centrés. Réutilisé : dans les contrôles (écran large) et,
 *  en écran externe, dans une colonne qui RÉPARTIT ces 3 lignes entre le bas du spectre et la barre. */
@Composable
private fun TrackMeta(t: Track) {
    Text(
        text = t.title,
        style = Sillon.type.display,
        color = Sillon.colors.texteIvoire,
        textAlign = TextAlign.Center,
        minLines = 2, // hauteur figée → pas de décalage au changement de titre
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
    t.album?.takeIf { it.isNotBlank() }?.let { album ->
        Text(
            text = album,
            style = Sillon.type.technique,
            color = Sillon.colors.texteSourdine,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
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
    tight: Boolean = false,
    queueFile: Boolean = false,
    onQueueFileChange: (Boolean) -> Unit = {},
    showMeta: Boolean = true, // titre/artiste/album DANS les contrôles (écran large) ; en externe ils sont
    onSetPane: (PlayerPane) -> Unit, // rendus séparément, répartis entre la pochette et la barre.
) {
    if (showMeta) {
        TrackMeta(t)
        Spacer(Modifier.height(Sillon.spacing.l))
    }

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

    // Provenance (TYPE de source : Jellyfin / Navidrome / Local — pas le nom du compte) + qualité.
    val servers by MusicRepository.servers.collectAsState()
    val provenance = servers.firstOrNull { it.id == t.serverId }?.type?.badge
    val info = listOfNotNull(provenance, t.qualityLabel()).joinToString("  ·  ")
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

    Spacer(Modifier.height(Sillon.spacing.l))

    val shuffle by PlayerController.shuffle.collectAsState()
    val repeatMode by PlayerController.repeatMode.collectAsState()
    // Icône Lecture/Pause sur l'INTENTION (ne clignote pas pendant le buffering d'un seek ±10 s) — T8.
    val wantPlay by PlayerController.playWhenReady.collectAsState()

    // Boutons LATÉRAUX à zone tactile SERRÉE (TransportButton ≈ icône + 4 dp) : un tap un peu À CÔTÉ ne
    // déclenche plus « avance/recule » (IconButton imposait 48 dp, donc un large anneau autour de l'icône).
    // En `tight` (colonne ~moitié de l'écran interne du Fold) on répartit en SpaceEvenly.
    // Transport : aléatoire, précédent, −10 s, lecture, +10 s, suivant, répéter.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (tight) Arrangement.SpaceEvenly
            else Arrangement.spacedBy(Sillon.spacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(Icons.Filled.Shuffle, "Aléatoire", tintIf(shuffle), 22.dp) { PlayerController.toggleShuffle() }
        TransportButton(Icons.Filled.SkipPrevious, "Précédent", Sillon.colors.texteIvoire, 30.dp) { PlayerController.previous() }
        TransportButton(Icons.Filled.Replay10, "−10 s", Sillon.colors.texteIvoire, 28.dp) { PlayerController.skipBackward() }
        IconButton(
            onClick = { PlayerController.togglePlayPause() },
            modifier = if (tight) Modifier.size(48.dp) else Modifier,
        ) {
            Icon(
                if (wantPlay) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (wantPlay) "Pause" else "Lecture",
                tint = Sillon.colors.accentCuivre,
                modifier = Modifier.size(if (tight) 44.dp else 54.dp),
            )
        }
        TransportButton(Icons.Filled.Forward10, "+10 s", Sillon.colors.texteIvoire, 28.dp) { PlayerController.skipForward() }
        TransportButton(Icons.Filled.SkipNext, "Suivant", Sillon.colors.texteIvoire, 30.dp) { PlayerController.next() }
        TransportButton(
            if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            "Répéter", tintIf(repeatMode != Player.REPEAT_MODE_OFF), 22.dp,
        ) { PlayerController.cycleRepeat() }
    }

    Spacer(Modifier.height(Sillon.spacing.xxl))

    // Volume SOUS le transport, descendu (plus d'espace au-dessus), volume média système.
    val volume by PlayerController.volume.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
    ) {
        IconButton(onClick = { PlayerController.nudgeVolume(up = false) }, modifier = Modifier.size(28.dp)) {
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
        IconButton(onClick = { PlayerController.nudgeVolume(up = true) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.VolumeUp, "Plus fort", tint = Sillon.colors.texteSourdine, modifier = Modifier.size(18.dp))
        }
    }

    // Boutons (favori/paroles/égaliseur/file) rapprochés du transport et du volume.
    Spacer(Modifier.height(Sillon.spacing.s))

    // Favori PISTE (cœur) + Ajouter à la file + Paroles + Égaliseur + (étroit) Titres de l'album.
    val favTracks by MusicRepository.favoriteTrackKeys.collectAsState()
    val isFav = t.matchKey() in favTracks
    val fileQueue by PlayerController.fileQueue.collectAsState()
    val inQueue = fileQueue.any { it.matchKey() == t.matchKey() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xl, Alignment.CenterHorizontally),
    ) {
        IconButton(onClick = { MusicRepository.toggleTrackFavorite(t) }) {
            Icon(
                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favori (titre)",
                tint = tintIf(isFav),
            )
        }
        // AJOUTER / RETIRER le titre EN COURS de la file d'attente manuelle. Passe en ✓ cuivre quand le
        // titre y est. (Le changement de VUE Album/File se fait via les pastilles du panneau.)
        IconButton(onClick = {
            if (inQueue) PlayerController.removeFromQueue(t) else PlayerController.addToQueue(t)
        }) {
            Icon(
                if (inQueue) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = if (inQueue) "Retirer de la file d'attente" else "Ajouter à la file d'attente",
                tint = tintIf(inQueue),
            )
        }
        /* ANCIEN — favori d'ALBUM (conservé au cas où ; le cœur fait déjà le favori PISTE) :
        val allAlbums by MusicRepository.albums.collectAsState()
        val favAlbums by MusicRepository.favorites.collectAsState()
        val currentAlbum = remember(allAlbums, t.album, t.artist) {
            val byTitle = allAlbums.filter { it.title == t.album }
            byTitle.firstOrNull { it.artist == t.artist } ?: byTitle.firstOrNull()
        }
        val isAlbumFav = currentAlbum != null && favAlbums.any { it.matchKey() == currentAlbum.matchKey() }
        if (currentAlbum != null) {
            IconButton(onClick = { currentAlbum?.let { MusicRepository.toggleFavorite(it) } }) {
                Icon(
                    if (isAlbumFav) Icons.Filled.LibraryAddCheck else Icons.Filled.LibraryAdd,
                    contentDescription = "Album favori",
                    tint = tintIf(isAlbumFav),
                )
            }
        }
        */
        IconButton(onClick = { onSetPane(if (pane == PlayerPane.LYRICS) PlayerPane.COVER else PlayerPane.LYRICS) }) {
            Icon(Icons.Filled.Lyrics, "Paroles", tint = tintIf(pane == PlayerPane.LYRICS))
        }
        IconButton(onClick = { onSetPane(if (pane == PlayerPane.EQUALIZER) PlayerPane.COVER else PlayerPane.EQUALIZER) }) {
            Icon(Icons.Filled.GraphicEq, "Égaliseur", tint = tintIf(pane == PlayerPane.EQUALIZER))
        }
        if (showQueue) {
            IconButton(onClick = {
                onQueueFileChange(false)
                onSetPane(if (pane == PlayerPane.QUEUE) PlayerPane.COVER else PlayerPane.QUEUE)
            }) {
                Icon(Icons.Filled.QueueMusic, "Titres de l'album", tint = tintIf(pane == PlayerPane.QUEUE && !queueFile))
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
            icon = Icons.Filled.Bluetooth
            // Nom de l'appareil + codec A2DP réel s'il a pu être lu (LDAC/aptX/AAC/SBC), ex. « Casque · LDAC ».
            val base = output.name ?: str(S.OUT_BLUETOOTH)
            label = listOfNotNull(base, output.codec).joinToString("  ·  ")
        }
        AudioOutputMonitor.Transport.WIRED -> {
            icon = Icons.Filled.Headphones; label = str(S.OUT_WIRED)
        }
        else -> {
            icon = Icons.Filled.Speaker; label = str(S.OUT_SPEAKER)
        }
    }
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            // Tap → sélecteur de sortie audio du système (équivalent AirPlay : HP / Bluetooth / casque…).
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent("android.settings.panel.action.MEDIA_OUTPUT").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            .padding(vertical = Sillon.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Sillon.colors.texteSourdine, modifier = Modifier.size(16.dp))
        Text(label, style = Sillon.type.technique, color = Sillon.colors.texteSourdine, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Filled.SwapHoriz, contentDescription = "Changer la sortie", tint = Sillon.colors.texteSourdine, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun tintIf(active: Boolean) =
    if (active) Sillon.colors.accentCuivre else Sillon.colors.texteSourdine

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
