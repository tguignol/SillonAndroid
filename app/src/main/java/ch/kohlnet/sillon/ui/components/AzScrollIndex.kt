package ch.kohlnet.sillon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kohlnet.sillon.ui.theme.Sillon

/** Liste des lettres de l'index (A → Z puis « # » pour les non-alphabétiques). */
val AZ_LETTERS: List<Char> = ('A'..'Z').toList() + '#'

/** Première lettre normalisée d'un libellé (A–Z), ou « # » si ça ne commence pas par une lettre. */
fun indexLetter(label: String): Char {
    val c = label.trim().firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

/** Clé de tri alphabétique : A→Z puis les non-alphabétiques (« # ») à la fin. */
fun azSortKey(label: String): String {
    val l = indexLetter(label)
    return (if (l == '#') "￿" else l.toString()) + label.trim().lowercase()
}

/** Index cible pour une lettre pressée : exact, sinon lettre présente suivante, sinon début. */
fun azTargetIndex(letters: List<Char>, c: Char): Int {
    if (c == '#') return letters.indexOfFirst { it == '#' }.let { if (it >= 0) it else 0 }
    val exact = letters.indexOfFirst { it == c }
    if (exact >= 0) return exact
    val next = letters.indexOfFirst { it in 'A'..'Z' && it > c }
    return if (next >= 0) next else 0
}

/**
 * Index alphabétique vertical (façon iOS) sur une bordure, HORS des vignettes. Presser ou glisser
 * le doigt sur une lettre appelle [onLetter] (ex. « M » → sauter aux M). Les lettres présentes dans
 * la liste sont en cuivre/gras, les absentes estompées. Un CURSEUR (pastille cuivre) marque la lettre
 * [current] (position de défilement actuelle) et se déplace quand on scrolle.
 */
@Composable
fun AzScrollIndex(present: Set<Char>, current: Char, onLetter: (Char) -> Unit, modifier: Modifier = Modifier) {
    var lastIdx by remember { mutableIntStateOf(-1) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = Sillon.spacing.s)
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Sur appui ET glissement : lettre sous le doigt → onLetter (anti-répétition).
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            lastIdx = -1
                            break
                        }
                        val h = size.height.toFloat()
                        val idx = ((change.position.y / h) * AZ_LETTERS.size).toInt()
                            .coerceIn(0, AZ_LETTERS.size - 1)
                        if (idx != lastIdx) {
                            lastIdx = idx
                            onLetter(AZ_LETTERS[idx])
                        }
                        change.consume()
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AZ_LETTERS.forEach { c ->
            val isCurrent = c == current
            Box(
                modifier = if (isCurrent) Modifier.size(16.dp).clip(CircleShape).background(Sillon.colors.accentCuivre) else Modifier,
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = c.toString(),
                    fontSize = 9.sp,
                    fontWeight = if (isCurrent || c in present) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isCurrent -> Color.White
                        c in present -> Sillon.colors.accentCuivre
                        else -> Sillon.colors.texteSourdine.copy(alpha = 0.45f)
                    },
                )
            }
        }
    }
}
