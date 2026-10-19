package com.vkmusic.data.api.vk

import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Friend
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

class VkApiAudioRepository(
    private val client: VkApiClient,
    private val userIdProvider: suspend () -> Long,
) : VkAudioRepository {

    override suspend fun getMyAudio(offset: Int, count: Int) = getUserAudio(userIdProvider(), offset, count)

    override suspend fun getUserAudio(ownerId: Long, offset: Int, count: Int): List<Track> =
        audioItems("audio.get", mapOf("owner_id" to ownerId, "offset" to offset, "count" to count))

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

    override suspend fun getMyPlaylists(): List<Playlist> {
        val me = userIdProvider()
        return client.call(
            "audio.getPlaylists",
            VkItems.serializer(PlaylistDto.serializer()),
            mapOf("owner_id" to me, "count" to 200),
        ).items.map { it.toPlaylist(me) }
    }

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

    override suspend fun getFriends(): List<Friend> =
        client.call(
            "friends.get",
            VkItems.serializer(UserDto.serializer()),
            mapOf("fields" to "photo_100,can_see_audio", "order" to "hints", "count" to 500),
        ).items
            .filter { it.canSeeAudio == 1 }
            .map { Friend(it.id, "${it.firstName} ${it.lastName}".trim(), it.photo100) }

    private suspend fun audioItems(method: String, params: Map<String, Any?>): List<Track> =
        client.call(method, VkItems.serializer(AudioDto.serializer()), params).items.map { it.toTrack() }
}

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
    )
}
