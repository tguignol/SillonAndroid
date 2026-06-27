package ch.kohlnet.sillon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.SillonTheme
import ch.kohlnet.sillon.ui.theme.placeholderBrush

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SillonTheme {
                AccueilScreen()
            }
        }
    }
}

/**
 * Premier écran « Accueil » — provisoire, pour valider le design system (palette cuivre/teal,
 * titre serif, données techniques en mono+teal, pochettes placeholder déterministes, espacements).
 * Sera remplacé par le vrai écran d'accueil (sections + grilles) calqué sur l'iOS.
 */
@Composable
fun AccueilScreen() {
    Scaffold(
        containerColor = Sillon.colors.fondNoir,
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Sillon.spacing.xl, vertical = Sillon.spacing.l),
            verticalArrangement = Arrangement.spacedBy(Sillon.spacing.l),
        ) {
            Text(
                text = "Accueil",
                style = Sillon.type.display,
                color = Sillon.colors.texteIvoire,
            )
            Text(
                text = "Albums récents",
                style = Sillon.type.displaySmall,
                color = Sillon.colors.texteSourdine,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.m)) {
                AlbumPlaceholder("No Need to Argue")
                AlbumPlaceholder("Wake Up and Smell the Coffee")
                AlbumPlaceholder("To the Faithful Departed")
            }
            Text(
                text = "FLAC · 44,1 kHz · ReplayGain",
                style = Sillon.type.technique,
                color = Sillon.colors.signalTeal,
            )
        }
    }
}

@Composable
private fun AlbumPlaceholder(titre: String) {
    Column(
        modifier = Modifier.width(96.dp),
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
                .background(placeholderBrush(titre)),
        )
        Text(
            text = titre,
            style = Sillon.type.corps,
            color = Sillon.colors.texteIvoire,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AccueilPreview() {
    SillonTheme { AccueilScreen() }
}
