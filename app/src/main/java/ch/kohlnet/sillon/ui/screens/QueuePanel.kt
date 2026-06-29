package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.data.Track
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.components.lazyColumnScrollbar
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.placeholderBrush
import coil3.compose.AsyncImage

/** Mode du panneau latéral : titres de l'ALBUM (défaut) ou la FILE D'ATTENTE complète. */
private enum class QueueMode { ALBUM, QUEUE }

/**
 * Coupe-circuit UI de la « file d'attente ». À `false`, toute l'UI de la file disparaît (chip du panneau
 * + action « Ajouter à la file » du menu ⋮) ; le panneau ne montre plus que les titres de l'album. TOUT
 * le code de la file (mode QUEUE, PlayerController.addToQueue/playNext, items du menu) reste en place →
 * il suffit de repasser à `true` pour la réactiver.
 */
const val QUEUE_UI_ENABLED = true

/**
 * Panneau latéral du lecteur. Bascule en haut :
 *  - « Album » (DÉFAUT) : l'INTÉGRALITÉ des titres de l'album du morceau courant (chargée du serveur,
 *    indépendamment de la file de lecture).
 *  - « File d'attente » : la file de lecture réelle = playlist « non officielle » que l'utilisateur mixe
 *    à la main (l'album par défaut + ce qu'il ajoute via « Lire ensuite » / « Ajouter à la file »).
 *    Masquée tant qu'elle est identique à l'album (rien de mixé).
 * Le morceau EN COURS est en cuivre ; tap = saut (ou lance l'album si le titre n'est pas dans la file).
 * Barre de défilement fine ; défile automatiquement vers le morceau courant.
 */
@Composable
fun QueuePanel(modifier: Modifier = Modifier, file: Boolean = false, onFileChange: (Boolean) -> Unit = {}) {
    // `queue` = file ACTIVE d'ExoPlayer ; `fileQueue` = file d'attente persistante (mix manuel). L'onglet
    // « File d'attente » montre `fileQueue` ; l'onglet sélectionné décide laquelle est la file active.
    // Le MODE est piloté de l'extérieur (`file`) pour que le bouton « File d'attente » du lecteur ET les
    // chips du panneau restent synchronisés.
    val queue by PlayerController.queue.collectAsState()
    val fileQueue by PlayerController.fileQueue.collectAsState()
    val current by PlayerController.current.collectAsState()
    val allAlbums by MusicRepository.albums.collectAsState()
    val listState = rememberLazyListState()

    // ALBUM = l'INTÉGRALITÉ des titres de l'album du morceau courant, chargée depuis le serveur
    // (indépendamment de la file de lecture). On retrouve l'album par titre+artiste (priorité au
    // serveur du morceau courant), puis on charge sa tracklist complète.
    val currentAlbum = remember(allAlbums, current?.album, current?.artist, current?.serverId) {
        val t = current
        val title = t?.album
        if (t == null || title.isNullOrBlank()) null
        else {
            val byTitle = allAlbums.filter { it.title == title }
            byTitle.firstOrNull { it.serverId == t.serverId }
                ?: byTitle.firstOrNull { it.artist == t.artist }
                ?: byTitle.firstOrNull()
        }
    }
    var albumTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(currentAlbum?.serverId, currentAlbum?.id) {
        albumTracks = currentAlbum?.let { MusicRepository.tracks(it) } ?: emptyList()
    }
    // Repli si l'album n'a pas pu être chargé : titres de l'album déjà présents dans la file.
    val albumList = remember(albumTracks, fileQueue, current) {
        if (albumTracks.isNotEmpty()) albumTracks
        else {
            val alb = current?.album
            (if (alb.isNullOrBlank()) fileQueue else fileQueue.filter { it.album == alb })
                .sortedBy { it.index ?: Int.MAX_VALUE }
        }
    }

    // Mode piloté par `file` : ALBUM = titres de l'album affiché ; QUEUE = file d'attente MANUELLE (seuls
    // les titres ajoutés par l'utilisateur, pouvant venir d'autres albums ; VIDE = on n'affiche rien).
    val mode = if (!QUEUE_UI_ENABLED || !file) QueueMode.ALBUM else QueueMode.QUEUE
    val items = if (mode == QueueMode.ALBUM) albumList else fileQueue

    // L'onglet sélectionné devient la file ACTIVE → le « suivant » suit l'album OU la file d'attente.
    // useQueue conserve le morceau courant et est no-op si la file active est déjà la bonne.
    val cur = current
    LaunchedEffect(mode, albumList, fileQueue, cur?.id, cur?.serverId) {
        if (!QUEUE_UI_ENABLED) return@LaunchedEffect
        if (mode == QueueMode.ALBUM) PlayerController.useQueue(albumList)
        else PlayerController.useQueue(fileQueue)
    }

    val currentIndex = items.indexOfFirst { t ->
        cur != null && ((t.id == cur.id && t.serverId == cur.serverId) || t.matchKey() == cur.matchKey())
    }
    LaunchedEffect(currentIndex, mode) {
        if (currentIndex >= 0) runCatching { listState.animateScrollToItem(currentIndex) }
    }

    Column(modifier) {
        // Rangée de bascule Album / File d'attente : masquée tant que la file est désactivée
        // (un seul mode → aucun choix à présenter). Le titre de l'album reste affiché juste dessous.
        if (QUEUE_UI_ENABLED) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Sillon.spacing.s, vertical = Sillon.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
            ) {
                QueueChip("Album", mode == QueueMode.ALBUM) { onFileChange(false) }
                QueueChip("File d'attente", mode == QueueMode.QUEUE) { onFileChange(true) }
            }
        }
        if (mode == QueueMode.ALBUM) {
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
        // File d'attente VIDE → on n'affiche aucune liste (juste une indication discrète).
        if (mode == QueueMode.QUEUE && items.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "File d'attente vide",
                    style = Sillon.type.corps,
                    color = Sillon.colors.texteSourdine,
                )
            }
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).lazyColumnScrollbar(listState, Sillon.colors.texteSourdine),
            contentPadding = PaddingValues(horizontal = Sillon.spacing.s, vertical = Sillon.spacing.xs),
        ) {
            itemsIndexed(items, key = { index, t -> "$index/${t.serverId}/${t.id}" }) { index, track ->
                val isCurrent = cur != null &&
                    ((track.id == cur.id && track.serverId == cur.serverId) || track.matchKey() == cur.matchKey())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // La liste affichée (album OU file manuelle) devient la file ACTIVE, départ à ce titre.
                            PlayerController.play(items, index)
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
