package com.vkmusic.data.api.fake

import android.content.Context
import android.net.Uri
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Friend
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeVkAudioRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : VkAudioRepository {

    private class FakeArtist(val ownerId: Long, val name: String, val album: String, val rootMidi: Int, val minor: Boolean, val titles: List<String>)

    private class FakePlaylist(var meta: Playlist, val trackIds: MutableList<String>)

    private class FakeFriend(val friend: Friend, val trackIds: List<String>)

    private class State(
        val catalog: List<Track>,
        val minorIds: Set<String>,
        val albums: List<FakePlaylist>,
        val library: MutableList<Track>,
        val playlists: MutableList<FakePlaylist>,
        val friends: List<FakeFriend>,
        var nextPlaylistId: Long = 100,
        var nextAudioId: Long = 1000,
        val copies: MutableMap<String, String> = mutableMapOf(),
    ) {
        fun original(id: String) = copies[id] ?: id
        fun byId(id: String) = catalog.first { it.id == original(id) }

        fun addCopy(track: Track): Track {
            val copy = track.copy(ownerId = ME, audioId = nextAudioId++)
            copies[copy.id] = original(track.id)
            return copy
        }

        fun playlist(ownerId: Long, playlistId: Long) =
            (playlists + albums).firstOrNull { it.meta.ownerId == ownerId && it.meta.playlistId == playlistId }
    }

    private val artists = listOf(
        FakeArtist(-101, "Синусоида", "Чистый тон", 57, true, listOf("Тестовая мелодия", "Нулевая фаза", "Ля минор", "Затухание")),
        FakeArtist(-102, "Осциллятор", "Генерация", 60, false, listOf("Квинта", "Арпеджио", "Тактовый сигнал", "Резонанс")),
        FakeArtist(-103, "Белый шум", "Спектр", 64, true, listOf("Минорный вечер", "Октава вниз", "Помехи", "Эфир")),
        FakeArtist(-104, "Генератор", "Переменный ток", 55, false, listOf("Мажорное утро", "Трезвучие", "Пятьдесят герц", "Сеть")),
        FakeArtist(-105, "Фильтр НЧ", "Срез", 52, true, listOf("Басовая линия", "Осенний тон", "Глубина", "Финальный аккорд")),
        FakeArtist(-106, "Дискретизация", "16 бит", 65, false, listOf("Найквист", "Квантование", "Отсчёт", "Буфер")),
    )

    private val mutex = Mutex()
    private var state: State? = null

    override suspend fun getMyAudio(offset: Int, count: Int) = withState { it.library.drop(offset).take(count) }

    override suspend fun getUserAudio(ownerId: Long, offset: Int, count: Int) = withState { s ->
        val ids = s.friends.firstOrNull { it.friend.id == ownerId }?.trackIds.orEmpty()
        ids.map(s::byId).drop(offset).take(count)
    }

    override suspend fun addAudio(track: Track) = withState { s ->
        s.library.firstOrNull { it.matchKey == track.matchKey } ?: s.addCopy(track).also { s.library.add(0, it) }
    }

    override suspend fun deleteAudio(track: Track) = withState { s ->
        s.library.removeAll { it.id == track.id }
        Unit
    }

    override suspend fun getMyPlaylists() = withState { s ->
        s.playlists.map { it.meta } + s.albums.take(FOLLOWED_ALBUMS).map { it.meta }
    }

    override suspend fun getPlaylistTracks(ownerId: Long, playlistId: Long, accessKey: String?) = withState { s ->
        s.playlist(ownerId, playlistId)?.trackIds?.map(s::byId).orEmpty()
    }

    override suspend fun createPlaylist(title: String) = withState { s ->
        val meta = Playlist(ME, s.nextPlaylistId++, title, subtitle = "Мой плейлист", isEditable = true)
        s.playlists.add(0, FakePlaylist(meta, mutableListOf()))
        meta
    }

    override suspend fun addToPlaylist(playlist: Playlist, tracks: List<Track>) = withState { s ->
        val target = s.playlist(playlist.ownerId, playlist.playlistId)
        require(target != null && target.meta.isEditable) { "Плейлист нельзя изменить" }
        tracks.map { s.original(it.id) }.filterNot(target.trackIds::contains).let(target.trackIds::addAll)
        target.meta = target.meta.copy(trackCount = target.trackIds.size)
    }

    override suspend fun search(query: String, offset: Int, count: Int) = withState { s ->
        s.catalog.filter { it.matches(query) }.drop(offset).take(count)
    }

    override suspend fun searchAlbums(query: String, count: Int) = withState { s ->
        s.albums.map { it.meta }
            .filter { it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true) }
            .take(count)
    }

    override suspend fun getRecommendations() = withState { s ->
        val libraryKeys = s.library.mapTo(HashSet()) { it.matchKey }
        s.catalog.filterNot { it.matchKey in libraryKeys }.shuffled().take(10)
    }

    override suspend fun getSimilar(ownerId: Long, audioId: Long) = withState { s ->
        val sourceId = s.original("${ownerId}_$audioId")
        val source = s.catalog.firstOrNull { it.id == sourceId } ?: return@withState emptyList<Track>()
        val minor = source.id in s.minorIds
        s.catalog
            .filter { it.id != source.id && (it.id in s.minorIds) == minor }
            .sortedByDescending { it.artist == source.artist }
    }

    override suspend fun getFriends() = withState { s -> s.friends.map { it.friend } }

    private fun Track.matches(query: String) =
        title.contains(query, ignoreCase = true) || artist.contains(query, ignoreCase = true)

    private suspend fun <T> withState(block: (State) -> T): T {
        delay(NETWORK_DELAY_MS)
        return mutex.withLock { block(state ?: createState().also { state = it }) }
    }

    private suspend fun createState(): State = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "fake_audio")
        val minorIds = mutableSetOf<String>()
        val catalog = artists.flatMapIndexed { artistIndex, artist ->
            artist.titles.mapIndexed { index, title ->
                val root = artist.rootMidi + listOf(0, 2, -3, 5)[index]
                val duration = 24 + (artistIndex * 4 + index * 3) % 14
                val file = File(dir, "${-artist.ownerId}_$index.wav")
                ToneTrackGenerator.generate(file, root, artist.minor, duration)
                Track(
                    ownerId = artist.ownerId,
                    audioId = index + 1L,
                    title = title,
                    artist = artist.name,
                    durationSec = duration,
                    url = Uri.fromFile(file).toString(),
                ).also { if (artist.minor) minorIds += it.id }
            }
        }
        val albums = artists.map { artist ->
            val ids = catalog.filter { it.ownerId == artist.ownerId }.map { it.id }
            FakePlaylist(
                Playlist(artist.ownerId, 1, artist.album, subtitle = artist.name, trackCount = ids.size, isAlbum = true),
                ids.toMutableList(),
            )
        }
        val favourites = catalog.filterIndexed { i, _ -> i % 5 == 0 }.map { it.id }
        val friendNames = listOf("Анна Тестова", "Иван Демонстрационный", "Мария Фейкова")
        State(
            catalog = catalog,
            minorIds = minorIds,
            albums = albums,
            library = mutableListOf(),
            playlists = mutableListOf(
                FakePlaylist(
                    Playlist(ME, 1, "Любимое", subtitle = "Мой плейлист", trackCount = favourites.size, isEditable = true),
                    favourites.toMutableList(),
                ),
            ),
            friends = friendNames.mapIndexed { i, name ->
                val ids = catalog.filterIndexed { index, _ -> index % 3 == i }.map { it.id }
                FakeFriend(Friend(1000L + i, name, trackCount = ids.size), ids)
            },
        ).apply {
            catalog.filterIndexed { i, _ -> i % 2 == 0 }.mapTo(library, ::addCopy)
        }
    }

    private companion object {
        const val ME = 1L
        const val FOLLOWED_ALBUMS = 3
        const val NETWORK_DELAY_MS = 300L
    }
}
