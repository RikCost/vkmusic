package com.vkmusic.data.repository

import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.db.TrackDao
import com.vkmusic.data.db.toEntity
import com.vkmusic.data.db.toTrack
import com.vkmusic.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

class LibraryIndex(tracks: List<Track> = emptyList()) {
    private val ids = tracks.mapTo(HashSet()) { it.id }
    private val keys = tracks.mapTo(HashSet()) { it.matchKey }

    operator fun contains(track: Track) = track.id in ids || track.matchKey in keys
}

@Singleton
class TrackRepository @Inject constructor(
    private val api: VkAudioRepository,
    private val dao: TrackDao,
) {
    val library: Flow<List<Track>> = dao.observeAll().map { entities -> entities.map { it.toTrack() } }

    val libraryIndex: Flow<LibraryIndex> = library.map(::LibraryIndex)

    suspend fun refreshLibrary() {
        val tracks = buildList {
            while (size < MAX_TRACKS) {
                val page = api.getMyAudio(offset = size, count = PAGE_SIZE)
                addAll(page)
                if (page.size < PAGE_SIZE) break
            }
        }.distinctBy { it.id }
        dao.replaceAll(tracks.mapIndexed { index, track -> track.toEntity(index) })
    }

    suspend fun addToLibrary(track: Track) {
        api.addAudio(track)
        refreshLibrary()
    }

    suspend fun removeFromLibrary(track: Track) {
        val library = library.first()
        val own = library.firstOrNull { it.id == track.id } ?: library.firstOrNull { it.matchKey == track.matchKey } ?: track
        api.deleteAudio(own)
        refreshLibrary()
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_TRACKS = 20_000
    }
}
