package com.vkmusic.data.api

import com.vkmusic.data.api.fake.FakeVkAudioRepository
import com.vkmusic.data.auth.SessionStore
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import com.vkmusic.data.web.VkWebAudioRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwitchingVkAudioRepository @Inject constructor(
    private val real: VkWebAudioRepository,
    private val fake: FakeVkAudioRepository,
    private val sessionStore: SessionStore,
) : VkAudioRepository {

    private suspend fun impl(): VkAudioRepository = if (sessionStore.current() != null) real else fake

    override suspend fun getProfile() = impl().getProfile()
    override suspend fun getMyAudio(offset: Int, count: Int) = impl().getMyAudio(offset, count)
    override suspend fun getUserAudio(ownerId: Long, offset: Int, count: Int) = impl().getUserAudio(ownerId, offset, count)
    override suspend fun getByIds(tracks: List<Track>) = impl().getByIds(tracks)
    override suspend fun addAudio(track: Track) = impl().addAudio(track)
    override suspend fun deleteAudio(track: Track) = impl().deleteAudio(track)
    override suspend fun getMyPlaylists() = impl().getMyPlaylists()
    override suspend fun getMyAlbums() = impl().getMyAlbums()
    override suspend fun getUserPlaylists(ownerId: Long) = impl().getUserPlaylists(ownerId)
    override suspend fun getPlaylist(ownerId: Long, playlistId: Long, accessKey: String?) =
        impl().getPlaylist(ownerId, playlistId, accessKey)
    override suspend fun getPlaylistTracks(ownerId: Long, playlistId: Long, accessKey: String?) =
        impl().getPlaylistTracks(ownerId, playlistId, accessKey)
    override suspend fun createPlaylist(title: String) = impl().createPlaylist(title)
    override suspend fun editPlaylist(playlist: Playlist, title: String, description: String) =
        impl().editPlaylist(playlist, title, description)
    override suspend fun deletePlaylist(playlist: Playlist) = impl().deletePlaylist(playlist)
    override suspend fun followPlaylist(playlist: Playlist) = impl().followPlaylist(playlist)
    override suspend fun addToPlaylist(playlist: Playlist, tracks: List<Track>) = impl().addToPlaylist(playlist, tracks)
    override suspend fun search(query: String, offset: Int, count: Int) = impl().search(query, offset, count)
    override suspend fun searchAlbums(query: String, count: Int) = impl().searchAlbums(query, count)
    override suspend fun getRecommendations() = impl().getRecommendations()
    override suspend fun getSimilar(ownerId: Long, audioId: Long) = impl().getSimilar(ownerId, audioId)
    override suspend fun getMixSettings() = impl().getMixSettings()
    override suspend fun getMix(mixId: String, entityId: String?, options: Map<String, List<String>>, count: Int) =
        impl().getMix(mixId, entityId, options, count)
    override suspend fun getLyrics(track: Track) = impl().getLyrics(track)
    override suspend fun getArtistTracks(artistId: String, offset: Int, count: Int) = impl().getArtistTracks(artistId, offset, count)
    override suspend fun getArtistAlbums(artistId: String) = impl().getArtistAlbums(artistId)
    override suspend fun getFriends() = impl().getFriends()
    override suspend fun getCommunities() = impl().getCommunities()
}
