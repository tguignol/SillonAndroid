package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.kohlnet.sillon.data.LyricsTranslator
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.data.Track
import ch.kohlnet.sillon.data.TrackLyrics
import ch.kohlnet.sillon.player.PlayerController
import ch.kohlnet.sillon.ui.theme.Sillon
import kotlinx.coroutines.launch

/** Langue cible de la traduction = langue de l'app (français). */
private const val TARGET_LANG = "fr"

/**
 * Paroles du morceau courant (façon iOS) : synchronisées (ligne en cours en cuivre + agrandie,
 * auto-défilement) ou simples. Bouton « Traduire » (ML Kit) si la langue détectée diffère de celle
 * de l'app → traduction en vert sous chaque ligne.
 */
@Composable
fun LyricsPanel(track: Track, modifier: Modifier = Modifier) {
    var lyrics by remember { mutableStateOf<TrackLyrics?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var detectedLang by remember { mutableStateOf<String?>(null) }
    var translations by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var showTranslation by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val position by PlayerController.positionMs.collectAsState()

    LaunchedEffect(track.id, track.serverId) {
        loaded = false
        lyrics = null
        translations = emptyMap()
        showTranslation = false
        detectedLang = null
        val l = MusicRepository.lyrics(track)
        lyrics = l
        loaded = true
        if (l != null && l.lines.isNotEmpty()) {
            val text = l.lines.joinToString("\n") { it.text }
            detectedLang = runCatching { LyricsTranslator.detectLanguage(text) }.getOrNull()
        }
    }

    val canTranslate = lyrics?.lines?.isNotEmpty() == true &&
        detectedLang != null &&
        detectedLang in LyricsTranslator.supported &&
        detectedLang != TARGET_LANG

    Box(modifier) {
        val l = lyrics
        when {
            !loaded -> CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = Sillon.colors.accentCuivre,
            )
            l == null || l.lines.isEmpty() -> Text(
                "Pas de paroles",
                style = Sillon.type.corps,
                color = Sillon.colors.texteSourdine,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> LyricsList(l, position, if (showTranslation) translations else emptyMap())
        }

        if (canTranslate) {
            TranslateChip(
                showingTranslation = showTranslation,
                loading = translating,
                modifier = Modifier.align(Alignment.TopEnd).padding(Sillon.spacing.s),
                onClick = {
                    if (showTranslation) {
                        showTranslation = false
                    } else {
                        showTranslation = true
                        val src = detectedLang
                        if (translations.isEmpty() && src != null && l != null) {
                            translating = true
                            scope.launch {
                                translations = runCatching {
                                    LyricsTranslator.translate(l.lines.map { it.text }, src, TARGET_LANG)
                                }.getOrDefault(emptyMap())
                                translating = false
                                if (translations.isEmpty()) showTranslation = false
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun LyricsList(lyrics: TrackLyrics, positionMs: Long, translations: Map<Int, String>) {
    val active = if (lyrics.synced) lyrics.activeLineIndex(positionMs / 1000.0) else null
    val listState = rememberLazyListState()

    LaunchedEffect(active) {
        val idx = active ?: return@LaunchedEffect
        runCatching {
            // Centrer verticalement la ligne en cours (façon Apple Music) au lieu de la coller en haut
            // → elle ne vient plus chevaucher le bouton « Traduire » resté en haut.
            val info = listState.layoutInfo
            val viewportH = info.viewportSize.height
            val itemH = info.visibleItemsInfo.firstOrNull { it.index == idx }?.size ?: 0
            listState.animateScrollToItem(idx, -((viewportH - itemH) / 2))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
        contentPadding = PaddingValues(vertical = Sillon.spacing.xxl, horizontal = Sillon.spacing.s),
    ) {
        itemsIndexed(lyrics.lines) { i, line ->
            Column(verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs)) {
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = if (i == active) Sillon.type.paroleActive else Sillon.type.corps,
                    color = when {
                        i == active -> Sillon.colors.accentCuivre
                        lyrics.synced -> Sillon.colors.texteSourdine
                        else -> Sillon.colors.texteIvoire
                    },
                )
                translations[i]?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = Sillon.type.corps, color = Sillon.colors.signalTeal)
                }
            }
        }
    }
}

@Composable
private fun TranslateChip(
    showingTranslation: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Sillon.colors.surfaceElevee)
            .clickable(onClick = onClick)
            .padding(horizontal = Sillon.spacing.m, vertical = Sillon.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Sillon.colors.signalTeal, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xs)) {
                Icon(
                    Icons.Filled.Translate,
                    contentDescription = null,
                    tint = if (showingTranslation) Sillon.colors.signalTeal else Sillon.colors.texteIvoire,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (showingTranslation) "Original" else "Traduire",
                    style = Sillon.type.technique,
                    color = if (showingTranslation) Sillon.colors.signalTeal else Sillon.colors.texteIvoire,
                )
            }
        }
    }
}
