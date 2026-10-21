package com.vkmusic.data.api.vk

import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Community
import com.vkmusic.data.model.Friend
import com.vkmusic.data.model.LyricLine
import com.vkmusic.data.model.Lyrics
import com.vkmusic.data.model.MixCategory
import com.vkmusic.data.model.MixOption
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Profile
import com.vkmusic.data.model.Track
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.putJsonArray

class VkApiAudioRepository(
    private val client: VkApiClient,
    private val json: Json,
    private val userIdProvider: suspend () -> Long,
) : VkAudioRepository {

    override suspend fun getProfile(): Profile {
        val user = client.call(
            "users.get",
            ListSerializer(UserDto.serializer()),
            mapOf("fields" to "photo_200,screen_name"),
        ).first()
        return Profile(user.id, "${user.firstName} ${user.lastName}".trim(), user.screenName, user.photo200)
    }

    override suspend fun getMyAudio(offset: Int, count: Int) = getUserAudio(userIdProvider(), offset, count)

    override suspend fun getUserAudio(ownerId: Long, offset: Int, count: Int): List<Track> =
        audioItems("audio.get", mapOf("owner_id" to ownerId, "offset" to offset, "count" to count))

    override suspend fun getByIds(tracks: List<Track>): List<Track> =
        tracks.chunked(BY_ID_CHUNK).flatMap { chunk ->
            client.call(
                "audio.getById",
                ListSerializer(AudioDto.serializer()),
                mapOf("audios" to chunk.joinToString(",") { t -> t.id + (t.accessKey?.let { "_$it" } ?: "") }),
            ).map { it.toTrack() }
        }

    override suspend fun addAudio(track: Track): Track {
        val newId = client.call(
            "audio.add",
            Long.serializer(),
            mapOf("audio_id" to track.audioId, "owner_id" to track.ownerId),
        )
        return track.copy(ownerId = userIdProvider(), audioId = newId)
    }

    override suspend fun deleteAudio(track: Track) {
        client.call("audio.delete", Int.serializer(), mapOf("audio_id" to track.audioId, "owner_id" to track.ownerId))
    }

    override suspend fun getMyPlaylists(): List<Playlist> = getUserPlaylists(userIdProvider())

    override suspend fun getUserPlaylists(ownerId: Long): List<Playlist> {
        val me = userIdProvider()
        return client.call(
            "audio.getPlaylists",
            VkItems.serializer(PlaylistDto.serializer()),
            mapOf("owner_id" to ownerId, "count" to 200),
        ).items.map { it.toPlaylist(me) }
    }

    override suspend fun getPlaylist(ownerId: Long, playlistId: Long, accessKey: String?): Playlist =
        client.call(
            "audio.getPlaylistById",
            PlaylistDto.serializer(),
            mapOf("owner_id" to ownerId, "playlist_id" to playlistId, "access_key" to accessKey),
        ).toPlaylist(userIdProvider())

    override suspend fun getPlaylistTracks(ownerId: Long, playlistId: Long, accessKey: String?): List<Track> =
        audioItems(
            "audio.get",
            mapOf("owner_id" to ownerId, "album_id" to playlistId, "access_key" to accessKey, "count" to 1000),
        )

    override suspend fun createPlaylist(title: String): Playlist {
        val me = userIdProvider()
        return client.call("audio.createPlaylist", PlaylistDto.serializer(), mapOf("owner_id" to me, "title" to title))
            .toPlaylist(me)
    }

    override suspend fun editPlaylist(playlist: Playlist, title: String, description: String) {
        client.call(
            "audio.editPlaylist",
            JsonElement.serializer(),
            mapOf(
                "owner_id" to playlist.ownerId,
                "playlist_id" to playlist.playlistId,
                "title" to title,
                "description" to description,
            ),
        )
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        val (ownerId, playlistId) = playlist.followCopyId?.let { userIdProvider() to it }
            ?: (playlist.ownerId to playlist.playlistId)
        client.call("audio.deletePlaylist", JsonElement.serializer(), mapOf("owner_id" to ownerId, "playlist_id" to playlistId))
    }

    override suspend fun followPlaylist(playlist: Playlist) {
        client.call(
            "audio.followPlaylist",
            JsonElement.serializer(),
            mapOf("owner_id" to playlist.ownerId, "playlist_id" to playlist.playlistId, "access_key" to playlist.accessKey),
        )
    }

    override suspend fun addToPlaylist(playlist: Playlist, tracks: List<Track>) {
        client.call(
            "audio.addToPlaylist",
            JsonElement.serializer(),
            mapOf(
                "owner_id" to playlist.ownerId,
                "playlist_id" to playlist.playlistId,
                "audio_ids" to tracks.joinToString(",") { it.id },
            ),
        )
    }

    override suspend fun search(query: String, offset: Int, count: Int): List<Track> =
        audioItems("audio.search", mapOf("q" to query, "offset" to offset, "count" to count, "auto_complete" to 1))

    override suspend fun searchAlbums(query: String, count: Int): List<Playlist> {
        val me = userIdProvider()
        return client.call(
            "audio.searchAlbums",
            VkItems.serializer(PlaylistDto.serializer()),
            mapOf("q" to query, "count" to count),
        ).items.map { it.toPlaylist(me) }
    }

    override suspend fun getRecommendations(): List<Track> =
        audioItems("audio.getRecommendations", mapOf("count" to 50))

    override suspend fun getSimilar(ownerId: Long, audioId: Long): List<Track> =
        audioItems("audio.getRecommendations", mapOf("target_audio" to "${ownerId}_$audioId", "count" to 50))

    // Meridius (core.js, ExploreRequests.getStreamMixSettings): mix_id=common, v=5.285,
    // ответ — {settings: {mix_categories: [...]}} или сразу объект с mix_categories.
    override suspend fun getMixSettings(): List<MixCategory> {
        val root = client.call(
            "audio.getStreamMixSettings",
            JsonElement.serializer(),
            mapOf("mix_id" to MIX_COMMON),
            version = MIX_API_VERSION,
        ).asObject() ?: return emptyList()
        val settings = root["settings"].asObject() ?: root
        return settings["mix_categories"].asArray().mapNotNull { element ->
            val category = element.asObject() ?: return@mapNotNull null
            val id = category["id"].asText() ?: return@mapNotNull null
            MixCategory(
                id = id,
                title = category["title"].asText() ?: id,
                options = category["options"].asArray().mapNotNull { option ->
                    val obj = option.asObject() ?: return@mapNotNull null
                    val optionId = obj["id"].asText() ?: return@mapNotNull null
                    MixOption(optionId, obj["title"].asText() ?: obj["name"].asText() ?: optionId)
                },
            )
        }
    }

    // Meridius (core.js, ExploreRequests.vkMix): POST audio.getStreamMixAudios, v=5.285,
    // mix_id/count/entity_id, выбранные фильтры — JSON-строкой в options. Ответ — массив аудио.
    override suspend fun getMix(mixId: String, entityId: String?, options: Map<String, List<String>>, count: Int): List<Track> {
        val selected = options.filterValues { it.isNotEmpty() }
        val response = client.call(
            "audio.getStreamMixAudios",
            JsonElement.serializer(),
            mapOf(
                "mix_id" to mixId,
                "count" to count,
                "entity_id" to entityId,
                "options" to selected.takeIf { it.isNotEmpty() }?.let { optionsJson(it) },
            ),
            version = MIX_API_VERSION,
        )
        val items = (response as? JsonArray) ?: response.asObject()?.get("items").asArray()
        val tracks = items.mapNotNull { runCatching { json.decodeFromJsonElement(AudioDto.serializer(), it) }.getOrNull() }
            .map { it.toTrack() }
        // Meridius дозапрашивает ссылки через getById; официальный клиент обычно получает url сразу.
        val missing = tracks.filter { it.url == null }
        if (missing.isEmpty()) return tracks
        val resolved = runCatching { getByIds(missing) }.getOrDefault(emptyList()).associateBy { it.id }
        return tracks.map { resolved[it.id] ?: it }
    }

    override suspend fun getLyrics(track: Track): Lyrics? {
        val root = client.call(
            "audio.getLyrics",
            JsonElement.serializer(),
            mapOf("audio_id" to track.id),
        ).asObject() ?: return null
        val lyrics = root["lyrics"].asObject() ?: root
        val timed = lyrics["timestamps"].asArray().mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val line = obj["line"].asText() ?: return@mapNotNull null
            LyricLine(line, (obj["begin"] as? JsonPrimitive)?.longOrNull)
        }
        if (timed.isNotEmpty()) return Lyrics(timed, isSynced = true)
        val text = when (val raw = lyrics["text"]) {
            is JsonArray -> raw.mapNotNull { it.asText() }
            is JsonPrimitive -> raw.contentOrNull?.lines().orEmpty()
            else -> emptyList()
        }
        return text.takeIf { it.isNotEmpty() }?.let { lines -> Lyrics(lines.map { LyricLine(it) }, isSynced = false) }
    }

    override suspend fun getArtistTracks(artistId: String, offset: Int, count: Int): List<Track> =
        audioItems("audio.getAudiosByArtist", mapOf("artist_id" to artistId, "offset" to offset, "count" to count))

    override suspend fun getArtistAlbums(artistId: String): List<Playlist> {
        val me = userIdProvider()
        return client.call(
            "audio.getAlbumsByArtist",
            VkItems.serializer(PlaylistDto.serializer()),
            mapOf("artist_id" to artistId, "count" to 100),
        ).items.map { it.toPlaylist(me) }
    }

    override suspend fun getFriends(): List<Friend> =
        client.call(
            "friends.get",
            VkItems.serializer(UserDto.serializer()),
            mapOf("fields" to "photo_100,can_see_audio", "order" to "hints", "count" to 500),
        ).items
            .filter { it.canSeeAudio == 1 }
            .map { Friend(it.id, "${it.firstName} ${it.lastName}".trim(), it.photo100) }

    override suspend fun getCommunities(): List<Community> =
        client.call(
            "groups.get",
            VkItems.serializer(GroupDto.serializer()),
            mapOf("extended" to 1, "fields" to "photo_100", "count" to 1000),
        ).items.map { Community(it.id, it.name, it.photo100) }

    private suspend fun audioItems(method: String, params: Map<String, Any?>): List<Track> =
        client.call(method, VkItems.serializer(AudioDto.serializer()), params).items.map { it.toTrack() }

    private fun optionsJson(options: Map<String, List<String>>): String =
        buildJsonObject {
            options.forEach { (category, values) -> putJsonArray(category) { values.forEach { add(JsonPrimitive(it)) } } }
        }.toString()

    private companion object {
        const val MIX_COMMON = "common"
        const val MIX_API_VERSION = "5.285"
        const val BY_ID_CHUNK = 100
    }
}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray).orEmpty()

private fun JsonElement?.asText(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun AudioDto.toTrack(): Track {
    val playableUrl = url?.takeIf { it.isNotBlank() && !it.contains("audio_api_unavailable") }
    return Track(
        ownerId = ownerId,
        audioId = id,
        title = title,
        artist = artist,
        durationSec = duration,
        url = playableUrl,
        coverUrl = album?.thumb?.best,
        isHls = playableUrl?.contains(".m3u8") == true,
        albumTitle = album?.title,
        isExplicit = isExplicit,
        hasLyrics = hasLyrics,
        artistId = mainArtists.firstOrNull { !it.id.isNullOrBlank() }?.id,
        accessKey = accessKey,
    )
}

internal fun PlaylistDto.toPlaylist(myId: Long): Playlist {
    val isAlbum = mainArtists.isNotEmpty()
    return Playlist(
        ownerId = original?.ownerId ?: ownerId,
        playlistId = original?.id ?: id,
        title = title,
        subtitle = mainArtists.joinToString { it.name }.ifEmpty { description },
        coverUrl = photo?.best ?: thumbs.firstOrNull()?.best,
        trackCount = count,
        isAlbum = isAlbum,
        isEditable = ownerId == myId && original == null && !isAlbum,
        accessKey = original?.accessKey ?: accessKey,
        description = description,
        plays = plays,
        updatedAt = updateTime,
        followCopyId = if (original != null && ownerId == myId) id else null,
    )
}
