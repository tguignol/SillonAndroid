package ch.kohlnet.sillon.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ch.kohlnet.sillon.ui.theme.Sillon
import ch.kohlnet.sillon.ui.theme.SillonTheme

/** Écran de configuration du widget (au moment de la pose) : choix du FOND — pochette colorée ou translucide. */
class SillonWidgetConfig : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED) // si l'utilisateur recule → le widget n'est pas posé
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent {
            SillonTheme(darkTheme = true) {
                ConfigScreen { translucent ->
                    WidgetPrefs.setTranslucent(this, id, translucent)
                    SillonWidget.update(this)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    finish()
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(onChoose: (Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sillon.colors.fondNoir)
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Fond du widget", style = Sillon.type.display, color = Sillon.colors.texteIvoire)
        OptionCard("Pochette colorée", "La pochette floutée en fond, façon Sortie média / Apple Music.") { onChoose(false) }
        OptionCard("Fond translucide", "Le papier peint transparaît derrière le widget.") { onChoose(true) }
    }
}

@Composable
private fun OptionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
            .background(Sillon.colors.surfaceElevee)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
    ) {
        Text(title, style = Sillon.type.corps, color = Sillon.colors.accentCuivre)
        Text(subtitle, style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
    }
}
