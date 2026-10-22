package com.vkmusic.data.repository

import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.db.TrackDao
import com.vkmusic.data.db.toEntity
import com.vkmusic.data.db.toTrack
import com.vkmusic.data.model.Track
import com.vkmusic.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
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
    @ApplicationScope scope: CoroutineScope,
) {
    // Читается из базы один раз с запуска приложения: тысячи строк «моей музыки» готовы к открытию
    // вкладки, а не грузятся заново на каждом экране.
    val library: Flow<List<Track>> = dao.observeAll()
        .map { entities -> entities.map { it.toTrack() } }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    val libraryIndex: Flow<LibraryIndex> = library.map(::LibraryIndex).shareIn(scope, SharingStarted.Eagerly, replay = 1)

    // Обновление, добавление и удаление идут по одному: иначе перечитанный за секунды до этого снимок
    // VK затирал только что добавленный или удалённый трек.
    private val libraryMutex = Mutex()

    suspend fun refreshLibrary() = libraryMutex.withLock { reloadLibrary() }

    private suspend fun reloadLibrary() {
        val tracks = buildList {
            while (size < MAX_TRACKS) {
                val page = api.getMyAudio(offset = size, count = PAGE_SIZE)
                addAll(page)
                if (page.size < PAGE_SIZE) break
            }
        }.distinctBy { it.id }
        dao.replaceAll(tracks.mapIndexed { index, track -> track.toEntity(index) })
    }

    // Один запрос к VK и правка одной строки в базе: полная перезагрузка «моей музыки» после каждого
    // нажатия занимала секунды. Целиком список перечитывается, только если VK не вернул добавленную копию.
    suspend fun addToLibrary(track: Track) = libraryMutex.withLock {
        val added = api.addAudio(track)
        // Тот же id — VK не вернул новую копию: настоящий id узнаём, перечитав библиотеку.
        if (added.id == track.id) reloadLibrary() else dao.insertFirst(added.toEntity(0))
    }

    suspend fun removeFromLibrary(track: Track) = libraryMutex.withLock {
        val library = library.first()
        val own = library.firstOrNull { it.id == track.id } ?: library.firstOrNull { it.matchKey == track.matchKey } ?: track
        api.deleteAudio(own)
        dao.delete(own.id)
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_TRACKS = 20_000
    }
}
