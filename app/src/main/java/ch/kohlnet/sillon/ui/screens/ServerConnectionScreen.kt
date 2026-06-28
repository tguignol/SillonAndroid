package ch.kohlnet.sillon.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ch.kohlnet.sillon.data.AppLanguage
import ch.kohlnet.sillon.data.AppSettings
import ch.kohlnet.sillon.data.AppearanceMode
import ch.kohlnet.sillon.data.ConnectionStatus
import ch.kohlnet.sillon.data.LanguageManager
import ch.kohlnet.sillon.data.MusicRepository
import ch.kohlnet.sillon.data.ServerConfig
import ch.kohlnet.sillon.data.ServerType
import ch.kohlnet.sillon.ui.components.ServerMark
import ch.kohlnet.sillon.ui.i18n.S
import ch.kohlnet.sillon.ui.i18n.str
import ch.kohlnet.sillon.ui.theme.Sillon

/** Réglages : apparence + langue + MULTI-SERVEUR. Sections repliables (Apparence, Ajouter un serveur). */
@Composable
fun ServerConnectionScreen() {
    val appearance by AppSettings.appearance.collectAsState()
    val servers by MusicRepository.servers.collectAsState()
    val status by MusicRepository.status.collectAsState()
    val refreshing by MusicRepository.refreshing.collectAsState()
    val refreshingServerId by MusicRepository.refreshingServerId.collectAsState()

    var type by rememberSaveable { mutableStateOf(ServerType.JELLYFIN) }
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<ServerConfig?>(null) }
    val connecting = status is ConnectionStatus.Connecting

    // Sélecteur de DOSSIER (SAF) pour la source « Fichiers locaux ». Lecture seule.
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            MusicRepository.addLocalServer(it.toString())
        }
    }

    editing?.let { server ->
        EditServerDialog(
            server = server,
            onDismiss = { editing = null },
            onSave = { name, u, usr, pwd ->
                MusicRepository.updateServer(server.id, name, u, usr, pwd)
                editing = null
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Sillon.spacing.xl)
            .padding(top = Sillon.spacing.l, bottom = Sillon.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Sillon.spacing.m),
    ) {
        Text(str(S.REGLAGES), style = Sillon.type.display, color = Sillon.colors.texteIvoire)

        // — Apparence (repliable) —
        CollapsibleSection(str(S.APPARENCE)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AppearanceMode.entries.forEachIndexed { i, mode ->
                    val label = when (mode) {
                        AppearanceMode.SYSTEM -> str(S.SYSTEME)
                        AppearanceMode.LIGHT -> str(S.CLAIR)
                        AppearanceMode.DARK -> str(S.SOMBRE)
                    }
                    SegmentedButton(
                        selected = appearance == mode,
                        onClick = { AppSettings.setAppearance(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, AppearanceMode.entries.size),
                    ) { Text(label, style = Sillon.type.corps) }
                }
            }
        }

        // — Langue —
        Text(str(S.LANGUE), style = Sillon.type.displaySmall, color = Sillon.colors.texteSourdine)
        LanguagePicker()

        Spacer(Modifier.height(Sillon.spacing.s))

        // — Serveurs configurés (repliable + rafraîchissement global) —
        CollapsibleSection(
            title = str(S.SERVEURS),
            trailing = {
                // Bouton STABLE (spinner à l'intérieur) ; clic propre, ne replie pas la section.
                IconButton(onClick = { MusicRepository.refresh() }, enabled = !refreshing) {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Sillon.colors.accentCuivre)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = str(S.RAFRAICHIR), tint = Sillon.colors.texteSourdine)
                    }
                }
            },
        ) {
            if (servers.isEmpty()) {
                Text(str(S.AUCUN_SERVEUR), style = Sillon.type.corps, color = Sillon.colors.texteSourdine)
            } else {
                servers.forEachIndexed { i, server ->
                    ServerRow(server, i, refreshingServerId, onEdit = { editing = server })
                }
                if (servers.size > 1) {
                    Text(
                        str(S.PRIORITE_HINT),
                        style = Sillon.type.technique,
                        color = Sillon.colors.texteSourdine,
                    )
                }
            }
        }

        Spacer(Modifier.height(Sillon.spacing.s))

        // — Ajouter un serveur (repliable, fermé par défaut) —
        CollapsibleSection(str(S.AJOUTER_SERVEUR), initiallyExpanded = false) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ServerType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = { type = t },
                        shape = SegmentedButtonDefaults.itemShape(i, ServerType.entries.size),
                    ) { Text(t.label, style = Sillon.type.corps) }
                }
            }
            if (type == ServerType.LOCAL) {
                // Fichiers locaux : on choisit un DOSSIER (pas d'URL/identifiants).
                Button(onClick = { folderPicker.launch(null) }, enabled = !connecting) {
                    Text(str(S.CHOISIR_DOSSIER), style = Sillon.type.corps)
                }
            } else {
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text(str(S.ADRESSE_SERVEUR)) },
                    placeholder = { Text("https://exemple:8096") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text(str(S.UTILISATEUR)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(str(S.MOT_DE_PASSE)) }, singleLine = true,
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
                ) { Text(str(S.AJOUTER), style = Sillon.type.corps) }
            }

            when (val s = status) {
                is ConnectionStatus.Connecting -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(str(S.CONNEXION_EN_COURS), style = Sillon.type.corps, color = Sillon.colors.texteSourdine)
                }
                is ConnectionStatus.Connected -> Text(
                    "${str(S.AJOUTE)} : ${s.name}", style = Sillon.type.corps, color = Sillon.colors.signalTeal,
                )
                is ConnectionStatus.Error -> Text(
                    s.message, style = Sillon.type.corps, color = MaterialTheme.colorScheme.error,
                )
                ConnectionStatus.Idle -> {}
            }
        }
    }
}

/** En-tête de section cliquable + chevron qui pivote ; contenu masquable (accordéon). */
@Composable
private fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Column(verticalArrangement = Arrangement.spacedBy(Sillon.spacing.m)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
                .clickable { expanded = !expanded }
                .padding(vertical = Sillon.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = Sillon.type.displaySmall,
                color = Sillon.colors.texteSourdine,
                modifier = Modifier.weight(1f),
            )
            trailing()
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = Sillon.colors.texteSourdine,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(Sillon.spacing.m)) { content() }
        }
    }
}

/** Dialogue d'édition d'un serveur existant. Mot de passe vide = inchangé (pas de re-validation). */
@Composable
private fun EditServerDialog(
    server: ServerConfig,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, user: String, password: String) -> Unit,
) {
    var name by remember { mutableStateOf(server.name) }
    var url by remember { mutableStateOf(server.baseUrl) }
    var user by remember { mutableStateOf(server.username) }
    var pwd by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(S.MODIFIER_SERVEUR), style = Sillon.type.displaySmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sillon.spacing.s)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(str(S.NOM)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text(str(S.ADRESSE_SERVEUR)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text(str(S.UTILISATEUR)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pwd, onValueChange = { pwd = it },
                    label = { Text(str(S.MDP_GARDER)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, url, user, pwd) }) { Text(str(S.ENREGISTRER)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(str(S.ANNULER)) }
        },
    )
}

/** Sélecteur de langue (11 langues, comme iOS) : ligne cliquable + menu déroulant. */
@Composable
private fun LanguagePicker() {
    val lang by LanguageManager.current.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Sillon.spacing.cardCorner))
                .clickable { expanded = true }
                .padding(vertical = Sillon.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.s),
        ) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = Sillon.colors.texteSourdine)
            Text(lang.displayName, style = Sillon.type.corps, color = Sillon.colors.texteIvoire, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Sillon.colors.texteSourdine)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { l ->
                DropdownMenuItem(
                    text = { Text(l.displayName, style = Sillon.type.corps) },
                    onClick = { LanguageManager.setLanguage(l); expanded = false },
                    trailingIcon = {
                        if (l == lang) Icon(Icons.Filled.Check, contentDescription = null, tint = Sillon.colors.accentCuivre)
                    },
                )
            }
        }
    }
}

@Composable
private fun ServerRow(server: ServerConfig, index: Int, refreshingServerId: String?, onEdit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sillon.spacing.xs),
    ) {
        ServerMark(server.type, Modifier.size(26.dp))
        // Taper le nom/adresse ouvre l'édition.
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Sillon.spacing.s))
                .clickable(onClick = onEdit)
                .padding(vertical = Sillon.spacing.xs),
        ) {
            Text(server.name, style = Sillon.type.corps, color = Sillon.colors.texteIvoire)
            Text(server.baseUrl, style = Sillon.type.technique, color = Sillon.colors.texteSourdine)
        }
        // Monter en priorité (le serveur du haut gagne sur les doublons).
        IconButton(onClick = { MusicRepository.moveServer(server.id, up = true) }, enabled = index > 0) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Monter en priorité",
                tint = if (index > 0) Sillon.colors.texteSourdine else Sillon.colors.texteSourdine.copy(alpha = 0.3f),
            )
        }
        // Rafraîchir CE serveur (reconnexion + relecture).
        IconButton(
            onClick = { MusicRepository.refreshServer(server.id) },
            enabled = refreshingServerId == null && server.active,
        ) {
            if (refreshingServerId == server.id) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Sillon.colors.accentCuivre)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = "Rafraîchir ce serveur", tint = Sillon.colors.texteSourdine)
            }
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
