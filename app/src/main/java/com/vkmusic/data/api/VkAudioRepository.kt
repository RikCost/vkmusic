package com.vkmusic.data.api

import com.vkmusic.data.model.Community
import com.vkmusic.data.model.Friend
import com.vkmusic.data.model.Lyrics
import com.vkmusic.data.model.MixCategory
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Profile
import com.vkmusic.data.model.Track

interface VkAudioRepository {
    suspend fun getProfile(): Profile

    suspend fun getMyAudio(offset: Int = 0, count: Int = 100): List<Track>
    suspend fun getUserAudio(ownerId: Long, offset: Int = 0, count: Int = 100): List<Track>
    suspend fun getByIds(tracks: List<Track>): List<Track>
    suspend fun addAudio(track: Track): Track
    suspend fun deleteAudio(track: Track)

    suspend fun getMyPlaylists(): List<Playlist>
    suspend fun getUserPlaylists(ownerId: Long): List<Playlist>
    suspend fun getPlaylist(ownerId: Long, playlistId: Long, accessKey: String? = null): Playlist
    suspend fun getPlaylistTracks(ownerId: Long, playlistId: Long, accessKey: String? = null): List<Track>
    suspend fun createPlaylist(title: String): Playlist
    suspend fun editPlaylist(playlist: Playlist, title: String, description: String)
    suspend fun deletePlaylist(playlist: Playlist)
    suspend fun followPlaylist(playlist: Playlist)
    suspend fun addToPlaylist(playlist: Playlist, tracks: List<Track>)

    suspend fun search(query: String, offset: Int = 0, count: Int = 50): List<Track>
    suspend fun searchAlbums(query: String, count: Int = 20): List<Playlist>
    suspend fun getRecommendations(): List<Track>
    suspend fun getSimilar(ownerId: Long, audioId: Long): List<Track>

    suspend fun getMixSettings(): List<MixCategory>

    /** VK Микс: [mixId] — `common` или `track_mix` (тогда [entityId] = id трека). */
    suspend fun getMix(mixId: String, entityId: String?, options: Map<String, List<String>>, count: Int): List<Track>

    suspend fun getLyrics(track: Track): Lyrics?

    suspend fun getArtistTracks(artistId: String, offset: Int = 0, count: Int = 100): List<Track>
    suspend fun getArtistAlbums(artistId: String): List<Playlist>

    suspend fun getFriends(): List<Friend>
    suspend fun getCommunities(): List<Community>
}
