package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ch.kohlnet.sillon.data.Album
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.ui.theme.Sillon
import kotlinx.coroutines.delay

/** Recherche d'albums sur le serveur (champ + résultats en grille). */
@Composable
fun RechercheScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Album>>(emptyList()) }
    var selected by remember { mutableStateOf<Album?>(null) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300) // anti-rebond
        results = runCatching { MusicRepository.searchAlbums(query) }.getOrDefault(emptyList())
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
            .padding(horizontal = Sillon.spacing.xl)
            .padding(top = Sillon.spacing.l),
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
    ) {
        Text("Recherche", style = Sillon.type.display, color = Sillon.colors.texteIvoire)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Albums, artistes…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AlbumGrid(results, Modifier.weight(1f)) { selected = it }
    }
}
