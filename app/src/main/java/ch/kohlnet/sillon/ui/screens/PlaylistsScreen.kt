package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.kohlnet.sillon.data.Playlists
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.components.TrackMenuButton
import ch.kohlnet.sillon.ui.components.lazyColumnScrollbar
import ch.kohlnet.sillon.ui.components.trackCountLabel
import ch.kohlnet.sillon.ui.i18n.S
import ch.kohlnet.sillon.ui.i18n.str
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.placeholderBrush
import coil3.compose.AsyncImage

/**
 * Section « Playlists » de la Bibliothèque (façon iOS `PlaylistsListView`) : liste des playlists locales,
 * création (bouton +), suppression, navigation vers le détail (réordonnancement). Navigation par état
 * local, comme le reste de l'app.
 */
@Composable
fun PlaylistsListScreen(onOpen: (String) -> Unit) {
    val playlists by Playlists.playlists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Le segment « Playlists » de la Bibliothèque sert déjà de titre → ici, juste l'action « + ».
        Row(
            Modifier.fillMaxWidth().padding(bottom = Sillon.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
        ) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Sillon.colors.accentCuivre, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(Sillon.spacing.xs))
                Text(str(S.NOUVELLE_PLAYLIST), style = Sillon.type.corps, color = Sillon.colors.accentCuivre)
            }
        }

        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(str(S.AUCUNE_PLAYLIST), style = Sillon.type.corps, color = Sillon.colors.texteSourdine)
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().lazyColumnScrollbar(listState, Sillon.colors.texteSourdine),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Sillon.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
            ) {
                itemsIndexed(playlists, key = { _, p -> p.id }) { _, pl ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(pl.id) }.padding(vertical = Sillon.spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
                    ) {
                        Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = Sillon.colors.accentCuivre, modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, style = Sillon.type.corps, color = Sillon.colors.texteIvoire, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(trackCountLabel(pl.tracks.size), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
                        }
                        IconButton(onClick = { Playlists.delete(pl.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = str(S.SUPPRIMER), tint = Sillon.colors.texteSourdine)
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        PlaylistNameDialog(
            title = str(S.NOUVELLE_PLAYLIST),
            initial = "",
            confirmLabel = str(S.CREER),
            onConfirm = { Playlists.create(it); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }
}

/**
 * Détail d'une playlist : lecture (tap = depuis ce titre), tout lire, monter/descendre chaque titre
 * (façon iOS `onMove`, ici via flèches comme demandé), retrait d'un titre, renommer / supprimer.
 */
@Composable
fun PlaylistDetailScreen(playlistId: String, onBack: () -> Unit) {
    val playlists by Playlists.playlists.collectAsState()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    if (playlist == null) { onBack(); return }
    val tracks = playlist.tracks
    val current by PlayerController.current.collectAsState()
    var showRename by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Sillon.spacing.s, vertical = Sillon.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Sillon.colors.texteIvoire)
            }
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = Sillon.type.display, color = Sillon.colors.texteIvoire, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(trackCountLabel(tracks.size), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
            }
            if (tracks.isNotEmpty()) {
                IconButton(onClick = { PlayerController.play(tracks, 0) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = str(S.LIRE), tint = Sillon.colors.accentCuivre)
                }
            }
            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Filled.Edit, contentDescription = str(S.RENOMMER), tint = Sillon.colors.texteSourdine)
            }
            IconButton(onClick = { Playlists.delete(playlist.id); onBack() }) {
                Icon(Icons.Filled.Delete, contentDescription = str(S.SUPPRIMER), tint = Sillon.colors.texteSourdine)
            }
        }

        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(str(S.PLAYLIST_VIDE), style = Sillon.type.corps, color = Sillon.colors.texteSourdine)
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().lazyColumnScrollbar(listState, Sillon.colors.texteSourdine),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Sillon.spacing.s, vertical = Sillon.spacing.xs),
            ) {
                itemsIndexed(tracks, key = { index, t -> "$index/${t.serverId}/${t.id}" }) { index, track ->
                    val isCurrent = current?.let { (track.id == it.id && track.serverId == it.serverId) || track.matchKey() == it.matchKey() } ?: false
                    Row(
                        Modifier.fillMaxWidth().clickable { PlayerController.play(tracks, index) }.padding(vertical = Sillon.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
                    ) {
                        AsyncImage(
                            model = track.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(Sillon.spacing.xs)).background(placeholderBrush(track.title.ifBlank { track.id })),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = Sillon.type.corps, color = if (isCurrent) Sillon.colors.accentCuivre else Sillon.colors.texteIvoire, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (track.artist.isNotBlank()) {
                                Text(track.artist, style = Sillon.type.technique, color = Sillon.colors.texteSourdine, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        // Monter / descendre (réordonnancement demandé) + retrait.
                        IconButton(onClick = { Playlists.moveUp(playlist.id, index) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Monter", tint = if (index > 0) Sillon.colors.texteIvoire else Sillon.colors.texteSourdine.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { Playlists.moveDown(playlist.id, index) }, enabled = index < tracks.lastIndex, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Descendre", tint = if (index < tracks.lastIndex) Sillon.colors.texteIvoire else Sillon.colors.texteSourdine.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { Playlists.removeAt(playlist.id, index) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = str(S.RETIRER_PLAYLIST), tint = Sillon.colors.texteSourdine, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        PlaylistNameDialog(
            title = str(S.RENOMMER),
            initial = playlist.name,
            confirmLabel = str(S.ENREGISTRER),
            onConfirm = { Playlists.rename(playlist.id, it); showRename = false },
            onDismiss = { showRename = false },
        )
    }
}

/** Boîte de dialogue de saisie d'un nom de playlist (création / renommage). */
@Composable
private fun PlaylistNameDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(str(S.NOM)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(S.ANNULER)) } },
        containerColor = Sillon.colors.surfaceElevee,
    )
}
