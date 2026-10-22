package com.vkmusic.data.web

import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.api.vk.VkApiAudioRepository
import com.vkmusic.data.model.Community
import com.vkmusic.data.model.Friend
import com.vkmusic.data.model.Lyrics
import com.vkmusic.data.model.MixCategory
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.PlaylistChoice
import com.vkmusic.data.model.Profile
import com.vkmusic.data.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Музыка через веб-версию VK, как в Meridius (meridius-core `AudioRequests`, `PlaylistsRequest`,
 * `SearchRequests`): al_audio.php по cookie сессии. То, что Meridius берёт из официального API
 * (VK Микс) или со страниц HTML, которые здесь не разбираются (профиль, друзья, сообщества,
 * исполнители, похожие), идёт через [official] с web-токеном.
 */
class VkWebAudioRepository(
    private val web: VkWebClient,
    private val official: VkApiAudioRepository,
    private val json: Json,
    private val userIdProvider: suspend () -> Long,
) : VkAudioRepository {

    private class Cursor(val tracks: MutableList<Track>, var more: CatalogMore?)

    private val cursorMutex = Mutex()
    private val cursors = mutableMapOf<Long, Cursor>()
    private val reloadLimit = Semaphore(RELOAD_CONCURRENCY)

    override suspend fun getProfile(): Profile = official.getProfile()

    override suspend fun getMyAudio(offset: Int, count: Int) = getUserAudio(userIdProvider(), offset, count)

    // Веб отдаёт аудио постранично через next_from; смещения интерфейса накладываются на загруженный префикс.
    override suspend fun getUserAudio(ownerId: Long, offset: Int, count: Int): List<Track> = cursorMutex.withLock {
        val cursor = cursors[ownerId]?.takeIf { offset > 0 } ?: firstPage(ownerId).also { cursors[ownerId] = it }
        while (cursor.tracks.size < offset + count) {
            val more = cursor.more ?: break
            val (tracks, next) = loadMore(more)
            cursor.more = next.takeIf { tracks.isNotEmpty() }
            cursor.tracks += tracks
        }
        cursor.tracks.drop(offset).take(count)
    }

    // Музыка владельца — его «плейлист» playlist_id=-1 (в Meridius так заведена «Моя музыка»:
    // playlist_id -1 у owner_id пользователя), load_section с is_loading_all=1 отдаёт список целиком.
    // Если так не вышло — section=all с продолжением через load_catalog_section (meridius-core AudioRequests.get).
    private suspend fun firstPage(ownerId: Long): Cursor {
        val me = userIdProvider()
        val all = runCatching { loadOwnerAudios(ownerId) }.getOrNull()
        if (!all.isNullOrEmpty()) return Cursor(all.toMutableList(), more = null)
        val page = section("all", ownerId)
        val body = page.getOrNull(1) as? JsonObject
        val list = (body?.get("playlist") as? JsonObject)?.get("list") ?: throw unexpected("музыку")
        return Cursor(WebAudioParser.tracks(list, me).toMutableList(), WebAudioParser.more(body))
    }

    // Если VK отдал меньше, чем в счётчике плейлиста, догружаем тем же load_section со смещением.
    private suspend fun loadOwnerAudios(ownerId: Long): List<Track> {
        val (info, first) = loadPlaylist(ownerId, OWNER_AUDIOS_PLAYLIST_ID, null)
        val tracks = first.toMutableList()
        val seen = tracks.mapTo(HashSet()) { it.id }
        while (tracks.size < info.trackCount && tracks.size < MAX_OWNER_TRACKS) {
            val next = loadPlaylist(ownerId, OWNER_AUDIOS_PLAYLIST_ID, null, offset = tracks.size).second.filter { seen.add(it.id) }
            if (next.isEmpty()) break
            tracks += next
        }
        return tracks
    }

    // Пустой список и «не тот ответ» — разные вещи: второе показываем, а не прячем за «пока нет треков».
    private fun unexpected(what: String) =
        IllegalStateException("VK не отдал $what через веб-версию. Проверьте «Диагностику веб-VK» в настройках.")

    private suspend fun loadMore(more: CatalogMore): Pair<List<Track>, CatalogMore?> {
        val me = userIdProvider()
        var page = loadCatalogSection(more)
        // meridius-core getDataWithMore: пустой ответ — VK не успел, повтор через 500 мс.
        if (page.isEmpty()) {
            delay(EMPTY_RETRY_MS)
            page = loadCatalogSection(more)
        }
        val body = page.getOrNull(1) as? JsonObject
        val list = (body?.get("playlist") as? JsonObject)?.get("list")
        val tracks = if (list != null) {
            WebAudioParser.tracks(list, me)
        } else {
            WebAudioParser.tracksFromHtml(htmlOf(page.getOrNull(0)), json, me)
        }
        return tracks to WebAudioParser.more(body)
    }

    private suspend fun loadCatalogSection(more: CatalogMore) =
        web.alAudio(mapOf("act" to "load_catalog_section", "section_id" to more.sectionId, "start_from" to more.nextFrom))

    private suspend fun section(name: String, ownerId: Long, extra: Map<String, Any?> = emptyMap()) =
        web.alAudio(mapOf("act" to "section", "claim" to 0, "is_layer" to 0, "owner_id" to ownerId, "section" to name) + extra)

    // reload_audios (meridius-core AudioRequests.getById/normalize): id_owner_actionHash_urlHash пачками по 10.
    override suspend fun getByIds(tracks: List<Track>): List<Track> {
        val me = userIdProvider()
        return coroutineScope {
            tracks.chunked(RELOAD_CHUNK).map { chunk ->
                async { reloadLimit.withPermit { reload(chunk.map(::reloadId), me) } }
            }.awaitAll().flatten()
        }
    }

    private suspend fun reload(ids: List<String>, me: Long): List<Track> {
        repeat(RELOAD_ATTEMPTS) {
            val result = web.alAudio(mapOf("act" to "reload_audios", "audio_ids" to ids.joinToString(","))).getOrNull(0)
            if (result is JsonArray) return WebAudioParser.tracks(result, me)
            delay(EMPTY_RETRY_MS)
        }
        return emptyList()
    }

    private fun reloadId(track: Track): String {
        val action = track.webHash(2)
        val url = track.webHash(5)
        return when {
            action.isNotEmpty() && url.isNotEmpty() -> "${track.id}_${action}_$url"
            !track.accessKey.isNullOrEmpty() -> "${track.id}_${track.accessKey}"
            else -> track.id
        }
    }

    override suspend fun addAudio(track: Track): Track {
        val addHash = track.webHash(0)
        if (addHash.isEmpty()) return official.addAudio(track)
        val me = userIdProvider()
        val result = web.alAudio(
            mapOf(
                "act" to "add",
                "audio_id" to track.audioId,
                "audio_owner_id" to track.ownerId,
                "from" to "user_list",
                "group_id" to 0,
                "hash" to addHash,
            ),
        )
        return WebAudioParser.track(result.getOrNull(0), me) ?: track.copy(ownerId = me)
    }

    override suspend fun deleteAudio(track: Track) {
        val deleteHash = track.webHash(3)
        if (deleteHash.isEmpty()) return official.deleteAudio(track)
        web.alAudio(
            mapOf("act" to "delete_audio", "aid" to track.audioId, "oid" to track.ownerId, "hash" to deleteHash, "restore" to 1),
        )
    }

    override suspend fun getMyPlaylists(): List<Playlist> = getUserPlaylists(userIdProvider())

    // Официальный список альбомов надёжнее (main_artists есть всегда), веб-список дополняет его теми,
    // что VK пометил как официальные; любой из источников может не ответить. Запрашиваются параллельно.
    override suspend fun getMyAlbums(): List<Playlist> = coroutineScope {
        val officialAlbums = async { runCatching { official.getMyAlbums() } }
        val webAlbums = async { runCatching { getMyPlaylists().filter { it.isAlbum } } }
        val fromOfficial = officialAlbums.await()
        val fromWeb = webAlbums.await()
        if (fromOfficial.isFailure && fromWeb.isFailure) throw fromOfficial.exceptionOrNull()!!
        (fromOfficial.getOrDefault(emptyList()) + fromWeb.getOrDefault(emptyList())).distinctBy { it.id }
    }

    // owner_playlists: payload[1] = [список, всего]; строка вместо списка — «Access denied».
    override suspend fun getUserPlaylists(ownerId: Long): List<Playlist> {
        val me = userIdProvider()
        val result = mutableListOf<Playlist>()
        while (result.size < MAX_PLAYLISTS) {
            val page = web.alAudio(
                mapOf(
                    "access_hash" to "",
                    "act" to "owner_playlists",
                    "is_attach" to 0,
                    "offset" to result.size,
                    "owner_id" to ownerId,
                    "isPlaylist" to true,
                ),
            )
            val list = page.getOrNull(0)
            if (list !is JsonArray) throw IllegalStateException("Плейлисты недоступны")
            val total = (page.getOrNull(1) as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            val parsed = list.mapNotNull { WebAudioParser.playlist(it, me) }
            if (parsed.isEmpty()) break
            result += parsed
            if (result.size >= total) break
        }
        return result.distinctBy { it.id }
    }

    override suspend fun getPlaylist(ownerId: Long, playlistId: Long, accessKey: String?): Playlist =
        loadPlaylist(ownerId, playlistId, accessKey).first

    override suspend fun getPlaylistTracks(ownerId: Long, playlistId: Long, accessKey: String?): List<Track> =
        loadPlaylist(ownerId, playlistId, accessKey).second

    // load_section type=playlist (meridius-core PlaylistsRequest.getPlaylist).
    private suspend fun loadPlaylist(ownerId: Long, playlistId: Long, accessKey: String?, offset: Int = 0): Pair<Playlist, List<Track>> {
        val me = userIdProvider()
        val context = when {
            !accessKey.isNullOrEmpty() -> ""
            ownerId == me -> "my"
            ownerId < 0 -> "group_list"
            else -> "user_playlists"
        }
        val page = web.alAudio(
            mapOf(
                "act" to "load_section",
                "access_hash" to accessKey.orEmpty(),
                "claim" to 0,
                "context" to context,
                "from_id" to me,
                "is_loading_all" to 1,
                "is_preload" to 0,
                "offset" to offset,
                "owner_id" to ownerId,
                "playlist_id" to playlistId,
                "type" to "playlist",
            ),
        )
        val first = page.getOrNull(0) as? JsonObject
        val obj = first?.get("playlist") as? JsonObject
            ?: first
            ?: ((page.getOrNull(1) as? JsonObject)?.get("playlist") as? JsonObject)
            ?: throw unexpected("плейлист")
        val playlist = WebAudioParser.playlist(obj, me)
            ?: Playlist(ownerId, playlistId, "", accessKey = accessKey)
        return playlist to WebAudioParser.tracks(obj["list"], me)
    }

    // save_playlist с хешем newPlaylistHash со страницы al_audio.php (meridius-core create/getNewHash).
    override suspend fun createPlaylist(title: String): Playlist {
        val me = userIdProvider()
        val hash = pageHash("newPlaylistHash") ?: throw IllegalStateException("Не удалось создать плейлист")
        val result = web.alAudio(
            mapOf(
                "Audios" to "",
                "act" to "save_playlist",
                "cover" to "",
                "description" to "",
                "hash" to hash,
                "owner_id" to me,
                "playlist_id" to 0,
                "title" to title,
                "isPlaylist" to true,
            ),
        )
        return WebAudioParser.playlist(result.getOrNull(0), me) ?: throw IllegalStateException("Не удалось создать плейлист")
    }

    override suspend fun editPlaylist(playlist: Playlist, title: String, description: String) {
        val (info, tracks) = loadPlaylist(playlist.ownerId, playlist.playlistId, playlist.accessKey)
        val hash = info.editHash ?: throw IllegalStateException("Плейлист нельзя изменить")
        web.alAudio(
            mapOf(
                "act" to "save_playlist",
                "Audios" to tracks.joinToString(",") { it.id },
                "cover" to 0,
                "description" to description,
                "hash" to hash,
                "no_discover" to 0,
                "owner_id" to info.ownerId,
                "playlist_id" to info.playlistId,
                "title" to title,
            ),
        )
    }

    // Свой плейлист — delete_playlist; чужой с подпиской — повторный follow_playlist снимает подписку.
    override suspend fun deletePlaylist(playlist: Playlist) {
        val info = if (playlist.editHash == null && playlist.followHash == null) getPlaylist(playlist.ownerId, playlist.playlistId, playlist.accessKey) else playlist
        val editHash = info.editHash
        if (editHash != null && !info.isFollowed) {
            web.alAudio(
                mapOf(
                    "act" to "delete_playlist",
                    "hash" to editHash,
                    "page_owner_id" to info.ownerId,
                    "playlist_id" to info.playlistId,
                    "playlist_owner_id" to info.ownerId,
                ),
            )
        } else {
            toggleFollow(info)
        }
    }

    override suspend fun followPlaylist(playlist: Playlist) {
        val info = if (playlist.followHash == null) getPlaylist(playlist.ownerId, playlist.playlistId, playlist.accessKey) else playlist
        toggleFollow(info)
    }

    private suspend fun toggleFollow(playlist: Playlist) {
        val hash = playlist.followHash ?: throw IllegalStateException("Подписка на этот плейлист недоступна")
        web.alAudio(
            mapOf("act" to "follow_playlist", "hash" to hash, "playlist_id" to playlist.playlistId, "playlist_owner_id" to playlist.ownerId),
        )
    }

    // Тот же more_playlists_add, что даёт хеш сохранения: VK отдаёт в нём свои плейлисты с отметкой трека.
    override suspend fun getPlaylistsForTrack(track: Track): List<PlaylistChoice> {
        val me = userIdProvider()
        val dialog = web.alAudio(
            mapOf("act" to "more_playlists_add", "audio_id" to track.audioId, "audio_owner_id" to track.ownerId, "owner_id" to me),
        )
        return dialog.getOrNull(2).text()?.let { WebAudioParser.playlistChoices(it, json) }
            ?: official.getPlaylistsForTrack(track)
    }

    override suspend fun addToPlaylist(playlist: Playlist, tracks: List<Track>) =
        changePlaylist(tracks, add = playlist.playlistId.toString(), remove = "", failure = "Не удалось добавить в плейлист")

    override suspend fun removeFromPlaylist(playlist: Playlist, tracks: List<Track>) =
        changePlaylist(tracks, add = "", remove = playlist.playlistId.toString(), failure = "Не удалось удалить из плейлиста")

    // save_audio_in_playlists с хешем из more_playlists_add (meridius-core addSong/getSaveHash): тот же
    // запрос, что у диалога «Добавить в плейлист» VK, — add_pl_ids добавляет, remove_pl_ids убирает.
    // Треки независимы, поэтому идут параллельно: несколько треков — не несколько ожиданий подряд.
    private suspend fun changePlaylist(tracks: List<Track>, add: String, remove: String, failure: String) {
        val me = userIdProvider()
        coroutineScope {
            tracks.map { track ->
                async {
                    reloadLimit.withPermit {
                        val dialog = web.alAudio(
                            mapOf("act" to "more_playlists_add", "audio_id" to track.audioId, "audio_owner_id" to track.ownerId, "owner_id" to me),
                        )
                        val hash = dialog.getOrNull(2).text()?.let { SAVE_HASH_REGEX.find(it)?.groupValues?.get(1) }
                            ?: throw IllegalStateException(failure)
                        web.alAudio(
                            mapOf(
                                "act" to "save_audio_in_playlists",
                                "add_pl_ids" to add,
                                "audio_id" to track.audioId,
                                "audio_owner_id" to track.ownerId,
                                "hash" to hash,
                                "owner_id" to me,
                                "remove_pl_ids" to remove,
                            ),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    // section=search (meridius-core SearchRequests.query): playlist.list или playlistData.list.
    // Поиск треков — официальный audio.search с web-токеном: веб-выдача VK делится на блоки,
    // и по ней одной полный релевантный список собрать не удалось. Веб-поиск Meridius — запасной путь.
    override suspend fun search(query: String, offset: Int, count: Int): List<Track> =
        runCatching { official.search(query, offset, count) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: webSearch(query, offset, count)

    // meridius-core SearchRequests.queryExtended: первые треки — playlists[0].list (иначе playlist.list),
    // продолжение — load_catalog_section по playlist.id/blockId и playlist.nextOffset (parseMore с forcePlaylist).
    private suspend fun webSearch(query: String, offset: Int, count: Int): List<Track> {
        val me = userIdProvider()
        val body = section("search", me, mapOf("q" to query)).getOrNull(1) as? JsonObject
            ?: throw unexpected("результаты поиска")
        val first = ((body["playlists"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("list")
            ?: (body["playlist"] as? JsonObject)?.get("list")
            ?: (body["playlistData"] as? JsonObject)?.get("list")
        val tracks = WebAudioParser.tracks(first, me).toMutableList()
        val seen = tracks.mapTo(HashSet()) { it.id }
        var more = searchMore(body)
        while (tracks.size < offset + count) {
            val current = more ?: break
            val (page, next) = loadMore(current)
            val fresh = page.filter { seen.add(it.id) }
            if (fresh.isEmpty()) break
            tracks += fresh
            more = next
        }
        return tracks.drop(offset).take(count)
    }

    private fun searchMore(body: JsonObject): CatalogMore? {
        val playlist = body["playlist"] as? JsonObject ?: return WebAudioParser.more(body)
        val sectionId = (playlist["id"] ?: playlist["blockId"]).text() ?: return null
        val nextFrom = playlist["nextOffset"].text() ?: return null
        return CatalogMore(sectionId, nextFrom)
    }

    // Альбомы в поиске Meridius вытаскивает из HTML-блоков; здесь — официальный метод, без него поиск не падает.
    override suspend fun searchAlbums(query: String, count: Int): List<Playlist> =
        runCatching { official.searchAlbums(query, count) }.getOrDefault(emptyList())

    override suspend fun getRecommendations(): List<Track> =
        runCatching { official.getRecommendations() }.getOrElse { error ->
            val me = userIdProvider()
            val body = section("recoms", me).getOrNull(1) as? JsonObject
            WebAudioParser.tracks((body?.get("playlist") as? JsonObject)?.get("list"), me).ifEmpty { throw error }
        }

    override suspend fun getSimilar(ownerId: Long, audioId: Long): List<Track> = official.getSimilar(ownerId, audioId)

    override suspend fun getMixSettings(): List<MixCategory> = official.getMixSettings()

    // Как в Meridius: audio.getStreamMixAudios официальным API, ссылки — через веб reload_audios.
    override suspend fun getMix(mixId: String, entityId: String?, options: Map<String, List<String>>, count: Int): List<Track> {
        val tracks = official.getMixTracks(mixId, entityId, options, count)
        val missing = tracks.filter { it.url == null }
        if (missing.isEmpty()) return tracks
        val resolved = runCatching { getByIds(missing) }.getOrDefault(emptyList()).associateBy { it.id }
        return tracks.map { resolved[it.id] ?: it }
    }

    // get_lyrics aid=<owner_id>_<id> (meridius-core AudioRequests.getLyrics).
    override suspend fun getLyrics(track: Track): Lyrics? =
        runCatching { WebAudioParser.lyrics(web.alAudio(mapOf("act" to "get_lyrics", "aid" to track.id)).getOrNull(0)) }
            .getOrNull()
            ?: official.getLyrics(track)

    override suspend fun getArtistTracks(artistId: String, offset: Int, count: Int) = official.getArtistTracks(artistId, offset, count)

    override suspend fun getArtistAlbums(artistId: String) = official.getArtistAlbums(artistId)

    override suspend fun getFriends(): List<Friend> = official.getFriends()

    override suspend fun getCommunities(): List<Community> = official.getCommunities()

    private suspend fun pageHash(name: String): String? =
        Regex("\"$name\":\"(.*?)\"").find(web.alAudioPage())?.groupValues?.get(1)

    private fun htmlOf(element: JsonElement?): String = when (element) {
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        is JsonArray -> element.joinToString("") { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
        else -> ""
    }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val RELOAD_CHUNK = 10
        const val RELOAD_CONCURRENCY = 4
        const val RELOAD_ATTEMPTS = 2
        const val EMPTY_RETRY_MS = 500L
        const val MAX_PLAYLISTS = 1000
        const val OWNER_AUDIOS_PLAYLIST_ID = -1L
        const val MAX_OWNER_TRACKS = 20_000
        val SAVE_HASH_REGEX = Regex("], '(.*)'")
    }
}
