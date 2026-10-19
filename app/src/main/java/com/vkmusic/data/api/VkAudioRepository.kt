package com.vkmusic.data.api

import com.vkmusic.data.model.Friend
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track

interface VkAudioRepository {
    suspend fun getMyAudio(offset: Int = 0, count: Int = 100): List<Track>
    suspend fun getUserAudio(ownerId: Long, offset: Int = 0, count: Int = 100): List<Track>
    suspend fun addAudio(track: Track): Track
    suspend fun deleteAudio(track: Track)

    suspend fun getMyPlaylists(): List<Playlist>
    suspend fun getPlaylistTracks(ownerId: Long, playlistId: Long, accessKey: String? = null): List<Track>
    suspend fun createPlaylist(title: String): Playlist
    suspend fun addToPlaylist(playlist: Playlist, tracks: List<Track>)

    suspend fun search(query: String, offset: Int = 0, count: Int = 50): List<Track>
    suspend fun searchAlbums(query: String, count: Int = 20): List<Playlist>
    suspend fun getRecommendations(): List<Track>
    suspend fun getSimilar(ownerId: Long, audioId: Long): List<Track>

    suspend fun getFriends(): List<Friend>
}
