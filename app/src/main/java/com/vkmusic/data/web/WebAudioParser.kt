package com.vkmusic.data.web

import com.vkmusic.data.model.LyricLine
import com.vkmusic.data.model.Lyrics
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.PlaylistChoice
import com.vkmusic.data.model.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Продолжение каталожного списка (meridius-core `parseMore`). */
data class CatalogMore(val sectionId: String, val nextFrom: String)

/**
 * Разбор ответов al_audio.php — порт `AudioStatic.getAudioAsObject` и `PlaylistsStatic.getPlaylistInfo`.
 * Аудио в вебе — массив, индексы полей из `Static.AudioObject`.
 */
object WebAudioParser {
    private const val INDEX_ID = 0
    private const val INDEX_OWNER_ID = 1
    private const val INDEX_URL = 2
    private const val INDEX_TITLE = 3
    private const val INDEX_PERFORMER = 4
    private const val INDEX_DURATION = 5
    private const val INDEX_LYRICS = 9
    private const val INDEX_FLAGS = 10
    private const val INDEX_HASHES = 13
    private const val INDEX_COVER_URL = 14
    private const val INDEX_MAIN_ARTISTS = 17
    private const val INDEX_ALBUM = 19
    private const val EXPLICIT_BIT = 1024

    private val dataAudioRegex = Regex("data-audio=\"(.*?)\"")
    private val backgroundRegex = Regex("background-image:\\s?url\\('?(.*?)'?\\)")
    private val tagRegex = Regex("<[^>]*>")
    private val numericEntityRegex = Regex("&#(x?)([0-9a-fA-F]+);")
    private val addToPlaylistsRegex = Regex("""addToPlaylistsBoxInit\(\s*-?\d+\s*,\s*-?\d+\s*,\s*-?\d+\s*,\s*(\[.*])\s*,\s*'[^']*'\s*\)""")

    fun track(element: JsonElement?, userId: Long): Track? {
        val item = element as? JsonArray ?: return null
        val id = item.text(INDEX_ID)?.toLongOrNull() ?: return null
        val ownerId = item.text(INDEX_OWNER_ID)?.toLongOrNull() ?: return null
        val rawUrl = item.text(INDEX_URL).orEmpty()
        val url = rawUrl.takeIf { it.isNotBlank() }
            ?.let { AudioUrlDecoder.decode(it, userId) }
            ?.takeUnless { it.contains("audio_api_unavailable") }
        val flags = item.text(INDEX_FLAGS)?.toIntOrNull() ?: 0
        val covers = item.text(INDEX_COVER_URL).orEmpty().replace("&amp;", "&").split(",")
        return Track(
            ownerId = ownerId,
            audioId = id,
            title = unescape(item.text(INDEX_TITLE)),
            artist = unescape(item.text(INDEX_PERFORMER)),
            durationSec = item.text(INDEX_DURATION)?.toIntOrNull() ?: 0,
            url = url,
            coverUrl = (covers.getOrNull(1) ?: covers.getOrNull(0))?.takeIf { it.isNotBlank() },
            isHls = url?.contains(".m3u8") == true,
            albumTitle = albumTitle(item.getOrNull(INDEX_ALBUM)),
            isExplicit = flags and EXPLICIT_BIT != 0,
            hasLyrics = item.getOrNull(INDEX_LYRICS).isTruthy(),
            artistId = (item.getOrNull(INDEX_MAIN_ARTISTS) as? JsonArray)
                ?.firstNotNullOfOrNull { (it as? JsonObject)?.get("id").text() },
            webHashes = item.text(INDEX_HASHES)?.takeIf { it.isNotBlank() },
        )
    }

    fun tracks(list: JsonElement?, userId: Long): List<Track> =
        (list as? JsonArray).orEmpty().mapNotNull { track(it, userId) }

    /** Старый формат каталога — HTML со строками `data-audio="[...]"` (meridius-core `builderHTML`). */
    fun tracksFromHtml(html: String, json: Json, userId: Long): List<Track> =
        dataAudioRegex.findAll(html).mapNotNull { match ->
            runCatching { json.parseToJsonElement(decodeEntities(match.groupValues[1])) }.getOrNull()
        }.mapNotNull { track(it, userId) }.toList()

    fun more(obj: JsonObject?): CatalogMore? {
        if (obj == null) return null
        val playlist = obj["playlist"] as? JsonObject
        val sectionId = obj.firstText("section_id", "sectionId")
            ?: playlist?.firstText("id", "blockId")
            ?: return null
        val nextFrom = obj.firstText("next_from", "nextFrom", "nextOffset")
            ?: playlist?.firstText("nextOffset", "next_from")
            ?: return null
        return CatalogMore(sectionId, nextFrom)
    }

    fun playlist(element: JsonElement?, myId: Long): Playlist? {
        val obj = when (element) {
            is JsonObject -> element
            is JsonArray -> return arrayPlaylist(element)
            else -> return null
        }
        val ownerId = obj.firstText("owner_id", "ownerId")?.toLongOrNull() ?: return null
        val playlistId = obj.firstText("playlist_id", "id")?.toLongOrNull() ?: return null
        val followed = listOf("followed", "isFollowed", "is_followed").any { obj[it].isTruthy() }
        val editHash = obj.firstText("editHash", "edit_hash")?.takeUnless { followed }
        val official = listOf("official", "isOfficial", "is_official").any { obj[it].isTruthy() }
        val cover = obj.firstText("coverUrl", "cover_url", "thumb")
            ?: obj.firstText("gridCovers")?.let { backgroundRegex.find(it)?.groupValues?.get(1) }
        return Playlist(
            ownerId = ownerId,
            playlistId = playlistId,
            title = unescape(obj.firstText("title", "name")),
            subtitle = unescape(obj.firstText("authorName", "authorLine", "subtitle")),
            coverUrl = cover?.replace("&amp;", "&"),
            trackCount = obj.firstText("size", "totalCount")?.toIntOrNull() ?: 0,
            isAlbum = official,
            isEditable = !editHash.isNullOrBlank() && ownerId == myId,
            accessKey = obj.firstText("accessHash", "access_hash"),
            description = unescape(obj.firstText("description")),
            plays = obj.firstText("listens")?.toLongOrNull() ?: 0,
            updatedAt = obj.firstText("lastUpdated", "last_updated")?.toLongOrNull() ?: 0,
            editHash = editHash,
            followHash = obj.firstText("followHash", "follow_hash"),
            isFollowed = followed,
        )
    }

    /**
     * more_playlists_add: окно VK «Добавить в плейлист» инициализируется вызовом
     * `AudioUtils.addToPlaylistsBoxInit(owner, me, audio, [[owner, id, "название", треков, есть_ли_трек], …], 'hash')`.
     */
    fun playlistChoices(script: String, json: Json): List<PlaylistChoice>? {
        val list = addToPlaylistsRegex.find(script)?.groupValues?.get(1) ?: return null
        val rows = runCatching { json.parseToJsonElement(list) }.getOrNull() as? JsonArray ?: return null
        return rows.mapNotNull { row ->
            val item = row as? JsonArray ?: return@mapNotNull null
            val playlist = Playlist(
                ownerId = item.text(0)?.toLongOrNull() ?: return@mapNotNull null,
                playlistId = item.text(1)?.toLongOrNull() ?: return@mapNotNull null,
                title = unescape(item.text(2)),
                trackCount = item.text(3)?.toIntOrNull() ?: 0,
                isEditable = true,
            )
            PlaylistChoice(playlist, containsTrack = item.getOrNull(4).isTruthy())
        }
    }

    /** get_lyrics: `lyrics.timestamps[{begin, line}]`, иначе `lyrics.text[]` или `lyrics.ugc[]`. */
    // Как в MeridiusNova (lyrics.get.ts): поля проверяются по очереди, пустое или null-поле не мешает
    // следующему — иначе при "text": null текст из ugc не находился. Текст может прийти и одной строкой с <br>.
    fun lyrics(element: JsonElement?): Lyrics? {
        val lyrics = (element as? JsonObject)?.get("lyrics") as? JsonObject ?: return null
        val timed = (lyrics["timestamps"] as? JsonArray).orEmpty().mapNotNull { line ->
            val obj = line as? JsonObject ?: return@mapNotNull null
            val begin = (obj["begin"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()
            LyricLine(unescape(obj["line"].text()), begin)
        }
        if (timed.isNotEmpty()) return Lyrics(timed, isSynced = timed.any { it.startMs != null })
        val text = listOf("text", "ugc").firstNotNullOfOrNull { key -> textLines(lyrics[key]).takeIf { it.isNotEmpty() } }
        return text?.let { lines -> Lyrics(lines.map { LyricLine(it) }, isSynced = false) }
    }

    private fun textLines(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.map { unescape(it.text()) }
        is JsonPrimitive -> element.text()?.let { unescape(it).lines() }.orEmpty()
        else -> emptyList()
    }.takeIf { lines -> lines.any { it.isNotBlank() } }.orEmpty()

    /** Как `unescape` Meridius (textContent): HTML-сущности раскрываются, теги отбрасываются. */
    fun unescape(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return decodeEntities(value.replace("<br>", "\n").replace(tagRegex, "")).trim()
    }

    private fun decodeEntities(value: String): String = value
        .replace(numericEntityRegex) { match ->
            val code = match.groupValues[2].toIntOrNull(if (match.groupValues[1].isEmpty()) 10 else 16)
            code?.let { String(Character.toChars(it)) } ?: match.value
        }
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")

    private fun arrayPlaylist(item: JsonArray): Playlist? = Playlist(
        ownerId = item.text(2)?.toLongOrNull() ?: return null,
        playlistId = item.text(0)?.toLongOrNull() ?: return null,
        title = unescape(item.text(1)),
        accessKey = item.text(3),
        coverUrl = item.text(4),
    )

    private fun albumTitle(element: JsonElement?): String? = when (element) {
        is JsonArray -> element.text(1)
        is JsonObject -> element.firstText("title", "name")
        is JsonPrimitive -> element.contentOrNull
        else -> null
    }?.let(::unescape)?.takeIf { it.isNotBlank() && !it.equals("unnamed", ignoreCase = true) }

    private fun JsonArray.text(index: Int): String? = getOrNull(index).text()

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.firstText(vararg keys: String): String? = keys.firstNotNullOfOrNull { this[it].text()?.takeIf(String::isNotBlank) }

    private fun JsonElement?.isTruthy(): Boolean {
        val primitive = this as? JsonPrimitive ?: return this is JsonObject || (this is JsonArray && isNotEmpty())
        primitive.booleanOrNull?.let { return it }
        val content = primitive.contentOrNull ?: return false
        return content.isNotEmpty() && content != "0" && content != "null"
    }
}
