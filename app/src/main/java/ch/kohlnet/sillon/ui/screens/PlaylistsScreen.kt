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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.data.Playlists
import ch.kohlnet.sillon.data.ServerPlaylist
import ch.kohlnet.sillon.data.ServerType
import ch.kohlnet.sillon.data.Track
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.components.ServerMark
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
fun PlaylistsListScreen(onOpenLocal: (String) -> Unit, onOpenServer: (ServerPlaylist) -> Unit) {
    val playlists by Playlists.playlists.collectAsState()
    val serverPlaylists by MusicRepository.serverPlaylists.collectAsState()
    val servers by MusicRepository.servers.collectAsState()
    val favKeys by Playlists.favoriteKeys.collectAsState()
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

        if (playlists.isEmpty() && serverPlaylists.isEmpty()) {
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
                // Playlists LOCALES (modifiables) : aucune icône de provenance + suppression via la corbeille.
                items(playlists, key = { "loc/${it.id}" }) { pl ->
                    val k = Playlists.favKeyLocal(pl.id)
                    PlaylistRow(name = pl.name, subtitle = trackCountLabel(pl.tracks.size), sourceType = null,
                        favorite = k in favKeys, onToggleFav = { Playlists.toggleFavorite(k) }, onOpen = { onOpenLocal(pl.id) }) {
                        IconButton(onClick = { Playlists.delete(pl.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = str(S.SUPPRIMER), tint = Sillon.colors.texteSourdine)
                        }
                    }
                }
                // Playlists SERVEUR (lecture seule) : favori + icône de provenance, pas de suppression.
                items(serverPlaylists, key = { "srv/${it.serverId}/${it.id}" }) { sp ->
                    val type = servers.firstOrNull { it.id == sp.serverId }?.type
                    val k = Playlists.favKeyServer(sp.serverId, sp.id)
                    PlaylistRow(name = sp.name, subtitle = trackCountLabel(sp.trackCount), sourceType = type,
                        favorite = k in favKeys, onToggleFav = { Playlists.toggleFavorite(k) }, onOpen = { onOpenServer(sp) })
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
 * Ligne de playlist (locale ou serveur) : icône, nom, sous-titre, ICÔNE DE PROVENANCE (logo du serveur)
 * — RIEN pour une playlist locale (`sourceType == null`) —, cœur favori, action à droite.
 */
@Composable
private fun PlaylistRow(
    name: String,
    subtitle: String,
    sourceType: ServerType?,
    favorite: Boolean,
    onToggleFav: () -> Unit,
    onOpen: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen() }.padding(vertical = Sillon.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
    ) {
        Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = Sillon.colors.accentCuivre, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = Sillon.type.corps, color = Sillon.colors.texteIvoire, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
        }
        // Icône de provenance (Jellyfin / Navidrome) ; aucune pour une playlist locale.
        if (sourceType != null) {
            ServerMark(sourceType, Modifier.size(18.dp))
        }
        IconButton(onClick = onToggleFav) {
            Icon(
                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favori",
                tint = if (favorite) Sillon.colors.accentCuivre else Sillon.colors.texteSourdine,
            )
        }
        trailing?.invoke()
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

/**
 * Liste des playlists FAVORITES (locales + serveur) — utilisée par l'onglet « Playlists » des Favoris.
 * Le cœur retire des favoris ; tap ouvre le détail (local éditable / serveur lecture seule).
 */
@Composable
fun FavoritePlaylistsList(onOpenLocal: (String) -> Unit, onOpenServer: (ServerPlaylist) -> Unit) {
    val playlists by Playlists.playlists.collectAsState()
    val serverPlaylists by MusicRepository.serverPlaylists.collectAsState()
    val servers by MusicRepository.servers.collectAsState()
    val favKeys by Playlists.favoriteKeys.collectAsState()
    val localFav = playlists.filter { Playlists.favKeyLocal(it.id) in favKeys }
    val serverFav = serverPlaylists.filter { Playlists.favKeyServer(it.serverId, it.id) in favKeys }

    if (localFav.isEmpty() && serverFav.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(str(S.AUCUN_FAVORI), style = Sillon.type.corps, color = Sillon.colors.texteSourdine)
        }
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().lazyColumnScrollbar(listState, Sillon.colors.texteSourdine),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Sillon.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
    ) {
        items(localFav, key = { "loc/${it.id}" }) { pl ->
            val k = Playlists.favKeyLocal(pl.id)
            PlaylistRow(name = pl.name, subtitle = trackCountLabel(pl.tracks.size), sourceType = null,
                favorite = true, onToggleFav = { Playlists.toggleFavorite(k) }, onOpen = { onOpenLocal(pl.id) })
        }
        items(serverFav, key = { "srv/${it.serverId}/${it.id}" }) { sp ->
            val type = servers.firstOrNull { it.id == sp.serverId }?.type
            val k = Playlists.favKeyServer(sp.serverId, sp.id)
            PlaylistRow(name = sp.name, subtitle = trackCountLabel(sp.trackCount), sourceType = type,
                favorite = true, onToggleFav = { Playlists.toggleFavorite(k) }, onOpen = { onOpenServer(sp) })
        }
    }
}

/**
 * Détail d'une playlist SERVEUR (LECTURE SEULE) : on charge ses titres, lecture (tap = depuis ce titre),
 * tout lire, et ⋮ pour copier un titre dans une playlist locale. La playlist serveur n'est JAMAIS modifiée.
 */
@Composable
fun ServerPlaylistDetailScreen(playlist: ServerPlaylist, onBack: () -> Unit) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(playlist.serverId, playlist.id) {
        tracks = MusicRepository.serverPlaylistTracks(playlist)
        loaded = true
    }
    val current by PlayerController.current.collectAsState()

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
                Text(trackCountLabel(if (loaded) tracks.size else playlist.trackCount), style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
            }
            if (tracks.isNotEmpty()) {
                IconButton(onClick = { PlayerController.play(tracks, 0) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = str(S.LIRE), tint = Sillon.colors.accentCuivre)
                }
            }
        }

        when {
            !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Sillon.colors.accentCuivre)
            }
            tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(str(S.PLAYLIST_VIDE), style = Sillon.type.corps, color = Sillon.colors.texteSourdine)
            }
            else -> {
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
                            TrackMenuButton(track)
                        }
                    }
                }
            }
        }
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
