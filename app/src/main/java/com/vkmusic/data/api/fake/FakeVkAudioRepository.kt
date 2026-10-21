package com.vkmusic.data.api.fake

import android.content.Context
import android.net.Uri
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
        val followedAlbums: MutableList<String>,
        var nextPlaylistId: Long = 100,
        var nextAudioId: Long = 1000,
        val copies: MutableMap<String, String> = mutableMapOf(),
    ) {
        fun original(id: String) = copies[id] ?: id
        fun byId(id: String) = catalog.first { it.id == original(id) }

        fun byIdOrNull(id: String) = library.firstOrNull { it.id == id } ?: catalog.firstOrNull { it.id == original(id) }

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

    override suspend fun getProfile() = Profile(ME, "Демо-пользователь", "demo")

    override suspend fun getMyAudio(offset: Int, count: Int) = withState { it.library.drop(offset).take(count) }

    override suspend fun getUserAudio(ownerId: Long, offset: Int, count: Int) = withState { s ->
        val tracks = if (ownerId < 0) {
            s.catalog.filter { it.ownerId == ownerId }
        } else {
            s.friends.firstOrNull { it.friend.id == ownerId }?.trackIds.orEmpty().map(s::byId)
        }
        tracks.drop(offset).take(count)
    }

    override suspend fun getByIds(tracks: List<Track>) = withState { s -> tracks.map { s.byIdOrNull(it.id) ?: it } }

    override suspend fun addAudio(track: Track) = withState { s ->
        s.library.firstOrNull { it.matchKey == track.matchKey } ?: s.addCopy(track).also { s.library.add(0, it) }
    }

    override suspend fun deleteAudio(track: Track) = withState { s ->
        s.library.removeAll { it.id == track.id }
        Unit
    }

    override suspend fun getMyPlaylists() = withState { s ->
        s.playlists.map { it.meta } + s.albums.filter { it.meta.id in s.followedAlbums }.map { it.meta }
    }

    override suspend fun getUserPlaylists(ownerId: Long) =
        if (ownerId == ME) getMyPlaylists() else withState { s -> s.albums.filter { it.meta.ownerId == ownerId }.map { it.meta } }

    override suspend fun getPlaylist(ownerId: Long, playlistId: Long, accessKey: String?) = withState { s ->
        requireNotNull(s.playlist(ownerId, playlistId)) { "Плейлист не найден" }.meta
    }

    override suspend fun editPlaylist(playlist: Playlist, title: String, description: String) = withState { s ->
        val target = s.playlist(playlist.ownerId, playlist.playlistId)
        require(target != null && target.meta.isEditable) { "Плейлист нельзя изменить" }
        target.meta = target.meta.copy(title = title, description = description)
    }

    override suspend fun deletePlaylist(playlist: Playlist) = withState { s ->
        s.playlists.removeAll { it.meta.id == playlist.id }
        s.followedAlbums.remove(playlist.id)
        Unit
    }

    override suspend fun followPlaylist(playlist: Playlist) = withState { s ->
        if (playlist.id !in s.followedAlbums) s.followedAlbums.add(playlist.id)
        Unit
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

    override suspend fun getMixSettings() = listOf(
        MixCategory(
            "moods",
            "Настроение",
            listOf(MixOption("happy", "Радостно"), MixOption("sad", "Грустно"), MixOption("active", "Активно"), MixOption("calm", "Спокойно")),
        ),
        MixCategory("recognitions", "Узнаваемость", listOf(MixOption("known", "Знакомое"), MixOption("unknown", "Незнакомое"))),
    )

    override suspend fun getMix(mixId: String, entityId: String?, options: Map<String, List<String>>, count: Int) = withState { s ->
        val moods = options["moods"].orEmpty()
        val calm = moods.any { it == "sad" || it == "calm" }
        val bright = moods.any { it == "happy" || it == "active" }
        val wantMinor = when {
            entityId != null -> s.original(entityId) in s.minorIds
            calm && !bright -> true
            bright && !calm -> false
            else -> null
        }
        val libraryKeys = s.library.mapTo(HashSet()) { it.matchKey }
        val recognitions = options["recognitions"].orEmpty()
        s.catalog
            .filter { wantMinor == null || (it.id in s.minorIds) == wantMinor }
            .filter { track ->
                when (recognitions) {
                    listOf("known") -> track.matchKey in libraryKeys
                    listOf("unknown") -> track.matchKey !in libraryKeys
                    else -> true
                }
            }
            .shuffled()
            .take(count)
    }

    override suspend fun getLyrics(track: Track) = withState { s ->
        val source = s.byIdOrNull(track.id) ?: track
        val lines = listOf(
            source.title,
            "Звучит ${source.artist}",
            "Это демо-текст",
            "Ноты идут по кругу",
            "И снова ${source.title.lowercase()}",
        )
        Lyrics(lines.mapIndexed { i, line -> LyricLine(line, i * LYRIC_STEP_MS) }, isSynced = true)
    }

    override suspend fun getArtistTracks(artistId: String, offset: Int, count: Int) = withState { s ->
        s.catalog.filter { it.artistId == artistId }.drop(offset).take(count)
    }

    override suspend fun getArtistAlbums(artistId: String) = withState { s ->
        s.albums.filter { it.meta.ownerId.toString() == artistId }.map { it.meta }
    }

    override suspend fun getFriends() = withState { s -> s.friends.map { it.friend } }

    override suspend fun getCommunities() = artists.take(COMMUNITIES).map { Community(-it.ownerId, "Фан-клуб «${it.name}»") }

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
                    albumTitle = artist.album,
                    hasLyrics = true,
                    artistId = artist.ownerId.toString(),
                ).also { if (artist.minor) minorIds += it.id }
            }
        }
        val albums = artists.map { artist ->
            val ids = catalog.filter { it.ownerId == artist.ownerId }.map { it.id }
            FakePlaylist(
                Playlist(
                    artist.ownerId,
                    1,
                    artist.album,
                    subtitle = artist.name,
                    trackCount = ids.size,
                    isAlbum = true,
                    description = "Демо-альбом исполнителя ${artist.name}",
                    plays = 1000L * (ids.size + artist.rootMidi),
                ),
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
            followedAlbums = albums.take(FOLLOWED_ALBUMS).mapTo(mutableListOf()) { it.meta.id },
        ).apply {
            catalog.filterIndexed { i, _ -> i % 2 == 0 }.mapTo(library, ::addCopy)
        }
    }

    private companion object {
        const val ME = 1L
        const val FOLLOWED_ALBUMS = 3
        const val COMMUNITIES = 3
        const val LYRIC_STEP_MS = 4_000L
        const val NETWORK_DELAY_MS = 300L
    }
}
