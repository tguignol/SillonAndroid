package ch.kohlnet.sillon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ch.kohlnet.sillon.data.AppSettings
import ch.kohlnet.sillon.data.AppearanceMode
import ch.kohlnet.sillon.data.ConnectionStatus
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.ui.theme.Sillon

/**
 * Réglages → Connexion au serveur. L'utilisateur saisit l'adresse + ses identifiants au runtime
 * (jamais committés). À la connexion, le dépôt s'authentifie et charge les albums (vus dans Accueil).
 */
@Composable
fun ServerConnectionScreen() {
    val status by MusicRepository.status.collectAsState()
    val appearance by AppSettings.appearance.collectAsState()

    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val connecting = status is ConnectionStatus.Connecting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Sillon.spacing.xl)
            .padding(top = Sillon.spacing.l),
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
    ) {
        Text("Réglages", style = Sillon.type.display, color = Sillon.colors.texteIvoire)

        Text("Apparence", style = Sillon.type.displaySmall, color = Sillon.colors.texteSourdine)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AppearanceMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = appearance == mode,
                    onClick = { AppSettings.setAppearance(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, AppearanceMode.entries.size),
                ) {
                    Text(mode.label, style = Sillon.type.corps)
                }
            }
        }

        Spacer(Modifier.height(Sillon.spacing.m))

        Text(
            "Connexion au serveur",
            style = Sillon.type.displaySmall,
            color = Sillon.colors.texteSourdine,
        )
        Spacer(Modifier.height(Sillon.spacing.s))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Adresse du serveur") },
            placeholder = { Text("https://exemple:8096") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Utilisateur") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mot de passe") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { MusicRepository.connect(url, user, password) },
            enabled = !connecting && url.isNotBlank() && user.isNotBlank(),
        ) {
            Text("Se connecter", style = Sillon.type.corps)
        }

        when (val s = status) {
            is ConnectionStatus.Idle -> {}
            is ConnectionStatus.Connecting -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Connexion…", style = Sillon.type.corps, color = Sillon.colors.texteSourdine)
            }
            is ConnectionStatus.Connected -> Text(
                "Connecté en tant que ${s.userName}",
                style = Sillon.type.corps,
                color = Sillon.colors.signalTeal,
            )
            is ConnectionStatus.Error -> Text(
                s.message,
                style = Sillon.type.corps,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (status is ConnectionStatus.Connected) {
            TextButton(onClick = { MusicRepository.disconnect() }) {
                Text(
                    "Se déconnecter",
                    style = Sillon.type.corps,
                    color = Sillon.colors.texteSourdine,
                )
            }
        }
    }
}
