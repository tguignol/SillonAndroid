package ch.kohlnet.sillon.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Source de fichiers LOCAUX (dossier choisi via SAF). `config.baseUrl` = URI d'arbre persistée.
 * LECTURE SEULE : on ne fait que lister/lire (jamais d'écriture/suppression).
 * Convention : un dossier qui contient des fichiers audio = un album ; son dossier parent = l'artiste ;
 * une image dans le dossier = la pochette. Lecture via l'URI `content://` (ExoPlayer).
 */
class LocalProvider(override val config: ServerConfig, private val context: Context) : ServerProvider {

    private val audioExts = setOf("flac", "alac", "wav", "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "aiff", "aif", "wma", "ape")
    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    private var scanned: List<Album>? = null
    private val audioByAlbum = mutableMapOf<String, List<DocumentFile>>()
    private val coverByAlbum = mutableMapOf<String, String?>()

    override suspend fun allAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(config.baseUrl))
            ?: return@withContext emptyList()
        audioByAlbum.clear(); coverByAlbum.clear()
        val out = mutableListOf<Album>()
        scan(root, null, out)
        out.sortedBy { it.title.lowercase() }.also { scanned = it }
    }

    private fun scan(dir: DocumentFile, parentName: String?, out: MutableList<Album>) {
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        val audio = children.filter { it.isFile && it.isAudio() }
        if (audio.isNotEmpty()) {
            val id = dir.uri.toString()
            val cover = children.firstOrNull { it.isFile && it.isImage() }?.uri?.toString()
            audioByAlbum[id] = audio
            coverByAlbum[id] = cover
            out.add(
                Album(
                    id = id,
                    title = dir.name ?: "Album",
                    artist = parentName.orEmpty(),
                    coverUrl = cover,
                    serverId = config.id,
                ),
            )
        }
        children.filter { it.isDirectory }.forEach { scan(it, dir.name, out) }
    }

    override suspend fun searchAlbums(query: String): List<Album> {
        val all = scanned ?: allAlbums()
        return all.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }

    override suspend fun albumsByArtistName(name: String): List<Album> {
        val all = scanned ?: allAlbums()
        return all.filter { it.artist.equals(name, ignoreCase = true) }
    }

    override suspend fun tracks(albumId: String): List<Track> = withContext(Dispatchers.IO) {
        val files = audioByAlbum[albumId] ?: return@withContext emptyList()
        val cover = coverByAlbum[albumId]
        files.sortedBy { it.name?.lowercase() }.mapIndexed { i, file ->
            val meta = readMeta(file.uri)
            Track(
                id = file.uri.toString(),
                title = meta.title ?: (file.name?.substringBeforeLast('.') ?: "Piste"),
                artist = meta.artist.orEmpty(),
                index = meta.track ?: (i + 1),
                durationMs = meta.durationMs,
                streamUrl = file.uri.toString(),
                coverUrl = cover,
                serverId = config.id,
                format = file.name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() },
                bitrateKbps = meta.bitrateKbps,
            )
        }
    }

    /** Paroles locales : `.lrc` à côté du fichier — non géré pour l'instant. */
    override suspend fun lyrics(trackId: String): TrackLyrics? = null

    override fun close() {}

    private data class Meta(
        val title: String?, val artist: String?, val track: Int?,
        val durationMs: Long?, val bitrateKbps: Int?,
    )

    private fun readMeta(uri: Uri): Meta {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            Meta(
                title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                track = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.trim()?.toIntOrNull(),
                durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                bitrateKbps = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000),
            )
        } catch (e: Exception) {
            Meta(null, null, null, null, null)
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun DocumentFile.isAudio(): Boolean {
        if (type?.startsWith("audio/") == true) return true
        return name?.substringAfterLast('.', "")?.lowercase() in audioExts
    }

    private fun DocumentFile.isImage(): Boolean {
        if (type?.startsWith("image/") == true) return true
        return name?.substringAfterLast('.', "")?.lowercase() in imageExts
    }

    companion object {
        /** Nom du dossier racine choisi (pour nommer la source). */
        fun folderName(context: Context, treeUri: String): String? =
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.name }.getOrNull()
    }
}
