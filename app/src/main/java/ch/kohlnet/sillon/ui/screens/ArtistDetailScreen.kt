package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ch.kohlnet.sillon.data.Album
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.ui.theme.Sillon

/** Détail d'un artiste : ses albums (grille). */
@Composable
fun ArtistDetailScreen(artistName: String, onBack: () -> Unit) {
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var selected by remember { mutableStateOf<Album?>(null) }

    LaunchedEffect(artistName) {
        albums = runCatching { MusicRepository.albumsByArtistName(artistName) }.getOrDefault(emptyList())
    }

    val sel = selected
    if (sel != null) {
        AlbumDetailScreen(sel, onBack = { selected = null })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Sillon.spacing.xl),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Sillon.colors.texteIvoire)
        }
        Text(artistName, style = Sillon.type.display, color = Sillon.colors.texteIvoire)
        Spacer(Modifier.height(Sillon.spacing.m))
        Text("Albums", style = Sillon.type.displaySmall, color = Sillon.colors.texteSourdine)
        Spacer(Modifier.height(Sillon.spacing.m))
        AlbumGrid(albums, Modifier.weight(1f)) { selected = it }
    }
}
