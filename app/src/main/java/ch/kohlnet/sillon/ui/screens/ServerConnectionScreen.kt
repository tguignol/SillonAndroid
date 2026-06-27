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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import ch.kohlnet.sillon.data.ServerConfig
import ch.kohlnet.sillon.data.ServerType
import ch.kohlnet.sillon.ui.theme.Sillon

/** Réglages : apparence + gestion MULTI-SERVEUR (liste de serveurs + ajout avec type). */
@Composable
fun ServerConnectionScreen() {
    val appearance by AppSettings.appearance.collectAsState()
    val servers by MusicRepository.servers.collectAsState()
    val status by MusicRepository.status.collectAsState()

    var type by rememberSaveable { mutableStateOf(ServerType.JELLYFIN) }
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val connecting = status is ConnectionStatus.Connecting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Sillon.spacing.xl)
            .padding(top = Sillon.spacing.l, bottom = Sillon.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
    ) {
        Text("Réglages", style = Sillon.type.display, color = Sillon.colors.texteIvoire)

        // — Apparence —
        Text("Apparence", style = Sillon.type.displaySmall, color = Sillon.colors.texteSourdine)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AppearanceMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = appearance == mode,
                    onClick = { AppSettings.setAppearance(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, AppearanceMode.entries.size),
                ) { Text(mode.label, style = Sillon.type.corps) }
            }
        }

        Spacer(Modifier.height(Sillon.spacing.s))

        // — Serveurs configurés —
        Text("Serveurs", style = Sillon.type.displaySmall, color = Sillon.colors.texteSourdine)
        if (servers.isEmpty()) {
            Text("Aucun serveur.", style = Sillon.type.corps, color = Sillon.colors.texteSourdine)
        } else {
            servers.forEach { server -> ServerRow(server) }
        }

        Spacer(Modifier.height(Sillon.spacing.s))

        // — Ajouter un serveur —
        Text("Ajouter un serveur", style = Sillon.type.displaySmall, color = Sillon.colors.texteSourdine)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ServerType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = type == t,
                    onClick = { type = t },
                    shape = SegmentedButtonDefaults.itemShape(i, ServerType.entries.size),
                ) { Text(t.label, style = Sillon.type.corps) }
            }
        }
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("Adresse du serveur") },
            placeholder = { Text("https://exemple:8096") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { Text("Utilisateur") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Mot de passe") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                MusicRepository.addServer(type, url, user, password)
                url = ""; user = ""; password = ""
            },
            enabled = !connecting && url.isNotBlank() && user.isNotBlank(),
        ) { Text("Ajouter", style = Sillon.type.corps) }

        when (val s = status) {
            is ConnectionStatus.Connecting -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Connexion…", style = Sillon.type.corps, color = Sillon.colors.texteSourdine)
            }
            is ConnectionStatus.Connected -> Text(
                "Ajouté : ${s.name}", style = Sillon.type.corps, color = Sillon.colors.signalTeal,
            )
            is ConnectionStatus.Error -> Text(
                s.message, style = Sillon.type.corps, color = MaterialTheme.colorScheme.error,
            )
            ConnectionStatus.Idle -> {}
        }
    }
}

@Composable
private fun ServerRow(server: ServerConfig) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
    ) {
        Column(Modifier.weight(1f)) {
            Text(server.name, style = Sillon.type.corps, color = Sillon.colors.texteIvoire)
            Text(server.baseUrl, style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
        }
        Switch(
            checked = server.active,
            onCheckedChange = { MusicRepository.setActive(server.id, it) },
            colors = SwitchDefaults.colors(checkedTrackColor = Sillon.colors.accentCuivre),
        )
        IconButton(onClick = { MusicRepository.removeServer(server.id) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = Sillon.colors.texteSourdine)
        }
    }
}
