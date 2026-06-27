package ch.kohlnet.sillon.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Modèle d'album côté UI (indépendant du serveur). */
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
)

/** État de la connexion au serveur, observé par l'UI. */
sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Connecting : ConnectionStatus
    data class Connected(val userName: String) : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

/**
 * Source de vérité unique pour la connexion serveur + la bibliothèque. Singleton (en mémoire pour
 * l'instant ; la persistance via DataStore viendra ensuite). Les identifiants ne sont JAMAIS écrits
 * en dur ni committés — saisis par l'utilisateur au runtime (cf. règle de sécurité).
 */
object MusicRepository {
    private var client: JellyfinClient? = null
    private var token: String? = null
    private var userId: String? = null

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    /** Authentifie puis charge les albums. Met à jour `status` et `albums`. */
    suspend fun connect(url: String, username: String, password: String) {
        _status.value = ConnectionStatus.Connecting
        try {
            client?.close()
            val c = JellyfinClient(url)
            val auth = c.authenticate(username, password)
            client = c
            token = auth.accessToken
            userId = auth.user.id
            _status.value = ConnectionStatus.Connected(auth.user.name)
            loadAlbums()
        } catch (e: Exception) {
            client = null
            token = null
            userId = null
            _albums.value = emptyList()
            _status.value = ConnectionStatus.Error(e.message ?: "Échec de la connexion")
        }
    }

    /** Recharge la liste des albums depuis le serveur connecté. */
    suspend fun loadAlbums() {
        val c = client ?: return
        val t = token ?: return
        val u = userId ?: return
        val items = c.albums(t, u)
        _albums.value = items.map { item ->
            Album(
                id = item.id,
                title = item.name,
                artist = item.albumArtist.orEmpty(),
                coverUrl = c.coverUrl(item.id, t),
            )
        }
    }
}
