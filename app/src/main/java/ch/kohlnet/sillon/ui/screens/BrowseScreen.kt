package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import ch.kohlnet.sillon.data.Album
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.ui.components.lazyColumnScrollbar
import ch.kohlnet.sillon.ui.theme.Sillon

private enum class BrowseMode { GENRE, DECADE }

/**
 * « Parcourir » (façon iOS) : filtrer la bibliothèque par GENRE ou par DÉCENNIE. Les valeurs sont
 * déduites des albums chargés (année + genre récupérés avec la liste) → pas de requête supplémentaire.
 * Sélectionner un genre/décennie ouvre une grille d'albums filtrée (même design que la bibliothèque).
 */
@Composable
fun BrowseScreen(onBack: () -> Unit) {
    val albums by MusicRepository.albums.collectAsState()
    var mode by rememberSaveable { mutableStateOf(BrowseMode.GENRE) }
    var genre by remember { mutableStateOf<String?>(null) }
    var decade by remember { mutableStateOf<Int?>(null) }
    var openAlbum by remember { mutableStateOf<Album?>(null) }

    openAlbum?.let { AlbumDetailScreen(it, onBack = { openAlbum = null }); return }
    genre?.let { g ->
        val list = albums.filter { it.genre?.trim()?.equals(g, ignoreCase = true) == true }
        FilteredAlbumsScreen(g, list, onBack = { genre = null }) { openAlbum = it }
        return
    }
    decade?.let { d ->
        val list = albums.filter { (it.year ?: 0) in d..(d + 9) }.sortedBy { it.year ?: 0 }
        FilteredAlbumsScreen("${d}s", list, onBack = { decade = null }) { openAlbum = it }
        return
    }

    val genres = remember(albums) {
        albums.mapNotNull { it.genre?.trim()?.takeIf { g -> g.isNotEmpty() } }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }
    val decades = remember(albums) {
        albums.mapNotNull { it.year?.takeIf { y -> y > 0 }?.let { y -> (y / 10) * 10 } }
            .distinct()
            .sortedDescending()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Sillon.spacing.xl)
            .padding(top = Sillon.spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Sillon.colors.texteIvoire)
            }
            Text("Parcourir", style = Sillon.type.display, color = Sillon.colors.texteIvoire)
        }
        Spacer(Modifier.height(Sillon.spacing.m))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = mode == BrowseMode.GENRE, onClick = { mode = BrowseMode.GENRE }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text("Genres", style = Sillon.type.corps)
            }
            SegmentedButton(selected = mode == BrowseMode.DECADE, onClick = { mode = BrowseMode.DECADE }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text("Décennies", style = Sillon.type.corps)
            }
        }
        Spacer(Modifier.height(Sillon.spacing.m))
        when (mode) {
            BrowseMode.GENRE ->
                if (genres.isEmpty()) BrowseEmpty("Aucun genre dans la bibliothèque")
                else BrowseRows(genres.map { it to it }) { genre = it }
            BrowseMode.DECADE ->
                if (decades.isEmpty()) BrowseEmpty("Aucune année dans la bibliothèque")
                else BrowseRows(decades.map { "${it}s" to it.toString() }) { decade = it.toIntOrNull() }
        }
    }
}

/** Liste de lignes « libellé › » (genres ou décennies). `value` est renvoyé au tap. */
@Composable
private fun BrowseRows(entries: List<Pair<String, String>>, onClick: (String) -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().lazyColumnScrollbar(listState, Sillon.colors.texteSourdine),
        contentPadding = PaddingValues(bottom = Sillon.spacing.xxl),
    ) {
        items(entries, key = { it.second }) { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(value) }
                    .padding(vertical = Sillon.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = Sillon.type.corps, color = Sillon.colors.texteIvoire, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Sillon.colors.texteSourdine)
            }
        }
    }
}

/** Grille d'albums filtrée (par genre ou décennie), même design que la bibliothèque. */
@Composable
private fun FilteredAlbumsScreen(title: String, albums: List<Album>, onBack: () -> Unit, onClick: (Album) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Sillon.spacing.xl)
            .padding(top = Sillon.spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Sillon.colors.texteIvoire)
            }
            Text(title, style = Sillon.type.display, color = Sillon.colors.texteIvoire, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Sillon.spacing.m))
        if (albums.isEmpty()) BrowseEmpty("Aucun album")
        else AlbumGrid(albums, Modifier.fillMaxSize()) { onClick(it) }
    }
}

@Composable
private fun BrowseEmpty(text: String) {
    Box(Modifier.fillMaxSize().padding(Sillon.spacing.xl), contentAlignment = Alignment.Center) {
        Text(text, style = Sillon.type.corps, color = Sillon.colors.texteSourdine, textAlign = TextAlign.Center)
    }
}
