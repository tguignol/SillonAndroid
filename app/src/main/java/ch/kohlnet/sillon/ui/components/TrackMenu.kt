package ch.kohlnet.sillon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.data.Playlists
import ch.kohlnet.sillon.data.Track
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.i18n.S
import ch.kohlnet.sillon.ui.i18n.str
import ch.kohlnet.sillon.ui.screens.QUEUE_UI_ENABLED
import ch.kohlnet.sillon.ui.theme.Sillon

/** « N titres » / « N titre » localisé. */
@Composable
fun trackCountLabel(n: Int): String = "$n " + if (n > 1) str(S.TITRES) else str(S.TITRE)

/**
 * Bouton « ⋮ » réutilisable sur une ligne de morceau (album, titres, favoris, détail playlist) — une
 * seule source de vérité pour les actions d'un titre, façon iOS [TrackMenuButton]. Entrée principale :
 * « Ajouter à une playlist ». Les actions de FILE D'ATTENTE restent code-only (cf. [QUEUE_UI_ENABLED]).
 */
@Composable
fun TrackMenuButton(track: Track, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Plus d'actions", tint = Sillon.colors.texteSourdine, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(str(S.AJOUTER_PLAYLIST), style = Sillon.type.corps) },
                onClick = { showAddToPlaylist = true; menuOpen = false },
            )
            // File d'attente désactivée → masquée, mais le code reste.
            if (QUEUE_UI_ENABLED) {
                DropdownMenuItem(
                    text = { Text(str(S.LIRE_ENSUITE), style = Sillon.type.corps) },
                    onClick = { PlayerController.playNext(track); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text(str(S.AJOUTER_FILE), style = Sillon.type.corps) },
                    onClick = { PlayerController.addToQueue(track); menuOpen = false },
                )
            }
        }
    }
    if (showAddToPlaylist) {
        AddToPlaylistSheet(listOf(track)) { showAddToPlaylist = false }
    }
}

/**
 * Feuille « Ajouter à une playlist » (façon iOS `AddToPlaylistView`) : choisir une playlist existante
 * ou en créer une à la volée. Ajoute `tracks` puis se ferme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(tracks: List<Track>, onDismiss: () -> Unit) {
    val playlists by Playlists.playlists.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreate by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Sillon.colors.surfaceElevee) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Sillon.spacing.l).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
        ) {
            Text(
                text = if (tracks.size == 1) str(S.AJOUTER_PLAYLIST) else "${str(S.AJOUTER_PLAYLIST)} (${tracks.size})",
                style = Sillon.type.display,
                color = Sillon.colors.texteIvoire,
            )

            // Choix 1 : CRÉER une nouvelle playlist → masque de saisie du titre (dialogue).
            Row(
                Modifier.fillMaxWidth().clickable { showCreate = true }.padding(vertical = Sillon.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Sillon.colors.accentCuivre)
                Text(str(S.NOUVELLE_PLAYLIST), style = Sillon.type.corps, color = Sillon.colors.texteIvoire)
            }

            // Choix 2 : ajouter à une playlist EXISTANTE.
            Text(str(S.MES_PLAYLISTS), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
            if (playlists.isEmpty()) {
                Text(str(S.AUCUNE_PLAYLIST), style = Sillon.type.corps, color = Sillon.colors.texteSourdine, modifier = Modifier.padding(vertical = Sillon.spacing.s))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs)) {
                    items(playlists, key = { it.id }) { pl ->
                        Row(
                            Modifier.fillMaxWidth().clickable { Playlists.addTracks(pl.id, tracks); onDismiss() }.padding(vertical = Sillon.spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
                        ) {
                            Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = Sillon.colors.accentCuivre, modifier = Modifier.size(24.dp))
                            Text(pl.name, style = Sillon.type.corps, color = Sillon.colors.texteIvoire, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${pl.tracks.size}", style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
                        }
                    }
                }
            }
            Spacer(Modifier.size(Sillon.spacing.l))
        }
    }

    // Masque de saisie du titre pour la nouvelle playlist : crée, y ajoute les titres, ferme tout.
    if (showCreate) {
        NameInputDialog(
            title = str(S.NOUVELLE_PLAYLIST),
            confirmLabel = str(S.CREER),
            onConfirm = { name ->
                val pl = Playlists.create(name)
                Playlists.addTracks(pl.id, tracks)
                showCreate = false
                onDismiss()
            },
            onDismiss = { showCreate = false },
        )
    }
}

/** Masque de saisie d'un titre de playlist (dialogue). */
@Composable
private fun NameInputDialog(title: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
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
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(S.ANNULER)) } },
        containerColor = Sillon.colors.surfaceElevee,
    )
}
