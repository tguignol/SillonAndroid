package ch.kohlnet.sillon.data

/** Provider Jellyfin : encapsule [JellyfinClient] et étiquette les résultats avec `config.id`. */
class JellyfinProvider(override val config: ServerConfig) : ServerProvider {
    private val client = JellyfinClient(config.baseUrl)
    private val token: String get() = config.token.orEmpty()
    private val userId: String get() = config.userId.orEmpty()

    override suspend fun allAlbums(): List<Album> =
        client.allAlbums(token, userId).map(::toAlbum)

    override suspend fun searchAlbums(query: String): List<Album> {
        val byName = client.searchAlbums(token, userId, query)
        val byArtist = client.searchArtists(token, userId, query).flatMap {
            runCatching { client.albumsByArtist(token, userId, it.id) }.getOrDefault(emptyList())
        }
        return (byName + byArtist).distinctBy { it.id }.map(::toAlbum)
    }

    override suspend fun albumsByArtistName(name: String): List<Album> {
        val artists = client.searchArtists(token, userId, name)
        val artist = artists.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: artists.firstOrNull() ?: return emptyList()
        return client.albumsByArtist(token, userId, artist.id).map(::toAlbum)
    }

    override suspend fun tracks(albumId: String): List<Track> =
        client.albumTracks(token, userId, albumId).map { tk ->
            val audio = tk.mediaStreams?.firstOrNull { it.type.equals("Audio", ignoreCase = true) }
            Track(
                id = tk.id,
                title = tk.name,
                artist = tk.artists?.joinToString(", ").orEmpty(),
                index = tk.index,
                disc = tk.disc,
                durationMs = tk.runTimeTicks?.div(10_000),
                streamUrl = client.streamUrl(tk.id, token),
                coverUrl = client.coverUrl(tk.id, token),
                serverId = config.id,
                album = tk.album,
                format = fileFormat(tk.container, tk.path, audio?.codec),
                sampleRateHz = audio?.sampleRate,
                bitDepthBits = audio?.bitDepth,
                bitrateKbps = audio?.bitRate?.let { it / 1000 },
            )
        }

    /** Format affiché : conteneur prioritaire, repli sur l'extension du chemin, puis le codec.
     *  Pour les conteneurs MP4 (m4a…), on désambigüe ALAC vs AAC via le codec (comme l'iOS). */
    private fun fileFormat(container: String?, path: String?, codec: String?): String? {
        val cont = container?.split(",")?.firstOrNull()?.trim()?.lowercase()
        val ext = path?.substringAfterLast('.', "")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val base = cont?.takeIf { it.isNotEmpty() } ?: ext
        val c = codec?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (base.isNullOrEmpty()) return c
        if (base in setOf("m4a", "m4b", "mp4", "mp4a", "mka", "mov") && c != null) return c
        return base
    }

    override suspend fun lyrics(trackId: String): TrackLyrics? {
        val dto = client.lyrics(token, trackId) ?: return null
        val lines = dto.lyrics.map { LyricLine(it.text, it.start?.let { ticks -> ticks / 10_000_000.0 }) }
        if (lines.isEmpty()) return null
        return TrackLyrics(synced = lines.any { it.timeSeconds != null }, lines = lines)
    }

    override fun close() = client.close()

    private fun toAlbum(item: JellyfinItem) =
        Album(
            item.id, item.name, item.albumArtist.orEmpty(), client.coverUrl(item.id, token), config.id,
            year = item.productionYear, genre = item.genres?.firstOrNull()?.takeIf { it.isNotBlank() },
        )

    companion object {
        suspend fun authenticate(id: String, url: String, username: String, password: String): ServerConfig {
            val c = JellyfinClient(url)
            try {
                val auth = c.authenticate(username, password)
                return ServerConfig(
                    id = id,
                    type = ServerType.JELLYFIN,
                    name = "${auth.user.name} · Jellyfin",
                    baseUrl = c.base,
                    username = username,
                    token = auth.accessToken,
                    userId = auth.user.id,
                )
            } finally {
                c.close()
            }
        }
    }
}
