package com.vkmusic.data.web

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Тот же алфавит, что у VK: стандартный base64 с переставленными «O» и «0».
    private val vkAlphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN0PQRSTUVWXYZO123456789+/="
    private val stdAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="

    private fun vkEncode(value: String): String =
        java.util.Base64.getEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.ISO_8859_1))
            .map { vkAlphabet[stdAlphabet.indexOf(it)] }
            .joinToString("")

    private fun masked(data: String, ops: String) =
        "https://vk.ru/mp3/audio_api_unavailable.mp3?extra=${vkEncode(data)}#${vkEncode(ops)}"

    @Test
    fun `url without mask is returned as is`() {
        val url = "https://cs1.vkuseraudio.net/s/v1/ac/x/index.m3u8?siren=1"
        assertEquals(url, AudioUrlDecoder.decode(url, 1))
    }

    @Test
    fun `reverse operation unmasks url`() {
        val url = "https://cs1.vkuseraudio.net/a/index.m3u8"
        assertEquals(url, AudioUrlDecoder.decode(masked(url.reversed(), "v"), 1))
    }

    @Test
    fun `operations are applied from the end of the list`() {
        val url = "https://cs1.vkuseraudio.net/b.mp3"
        // Сначала (с конца списка) xor с ключом «!», затем разворот: замаскировано в обратном порядке.
        val data = url.reversed().map { (it.code xor '!'.code).toChar() }.joinToString("")
        assertEquals(url, AudioUrlDecoder.decode(masked(data, "v\u0009x\u000B!"), 1))
    }

    @Test
    fun `broken mask keeps original url`() {
        val broken = "https://vk.ru/mp3/audio_api_unavailable.mp3?extra=abc#${vkEncode("q")}"
        assertEquals(broken, AudioUrlDecoder.decode(broken, 1))
    }

    @Test
    fun `audio array is mapped to track with lazy url`() {
        val item = json.parseToJsonElement(
            """
            [123, -100, "", "Title &amp; Co", "Artist", 200, 0, 0, "", 1, 1024, "", "{}",
             "addh/edith/acth/delh/reph/urlh", "https://s.jpg,https://p.jpg", "", "",
             [{"id":"art1","name":"A"}], [], [5, "Album &quot;X&quot;", -100, "hash"]]
            """.trimIndent(),
        )

        val track = requireNotNull(WebAudioParser.track(item, 42))

        assertEquals("-100_123", track.id)
        assertEquals("Title & Co", track.title)
        assertNull(track.url)
        assertTrue(track.isPlayable)
        assertTrue(track.isExplicit)
        assertTrue(track.hasLyrics)
        assertEquals("https://p.jpg", track.coverUrl)
        assertEquals("Album \"X\"", track.albumTitle)
        assertEquals("art1", track.artistId)
        assertEquals("acth", track.webHash(2))
        assertEquals("urlh", track.webHash(5))
    }

    @Test
    fun `playlist info marks own playlist editable and followed one not`() {
        val own = WebAudioParser.playlist(
            json.parseToJsonElement("""{"id":7,"ownerId":42,"title":"Mine","editHash":"e","followHash":"f","size":"3","listens":"10"}"""),
            myId = 42,
        )!!
        val followed = WebAudioParser.playlist(
            json.parseToJsonElement("""{"id":8,"ownerId":5,"title":"Theirs","editHash":"e","followHash":"g","isFollowed":true}"""),
            myId = 42,
        )!!

        assertTrue(own.isEditable)
        assertEquals(3, own.trackCount)
        assertEquals(10L, own.plays)
        assertFalse(followed.isEditable)
        assertTrue(followed.isFollowed)
        assertNull(followed.editHash)
        assertEquals("g", followed.followHash)
    }

    @Test
    fun `catalog more and lyrics are parsed`() {
        val more = WebAudioParser.more(json.parseToJsonElement("""{"section_id":"s1","next_from":"n1"}""") as kotlinx.serialization.json.JsonObject)
        assertEquals(CatalogMore("s1", "n1"), more)

        val lyrics = requireNotNull(
            WebAudioParser.lyrics(json.parseToJsonElement("""{"lyrics":{"timestamps":[{"begin":1000,"line":"a &amp; b"}]}}""")),
        )
        assertTrue(lyrics.isSynced)
        assertEquals("a & b", lyrics.lines.single().text)
        assertEquals(1000L, lyrics.lines.single().startMs)
    }

    @Test
    fun `playlist choices are read from add to playlists box`() {
        val script = """try{uiSearch.init('audio_atp_search', {});} catch (e) {};AudioUtils.addToPlaylistsBoxInit(10, 10, 456, """ +
            """[[10,404,"слушать &amp; петь",1,1],[10,136,"Для тебя",91,0]], 'abc123')"""
        val choices = requireNotNull(WebAudioParser.playlistChoices(script, json))
        assertEquals(listOf("10_404", "10_136"), choices.map { it.playlist.id })
        assertEquals("слушать & петь", choices[0].playlist.title)
        assertEquals(91, choices[1].playlist.trackCount)
        assertEquals(listOf(true, false), choices.map { it.containsTrack })
        assertTrue(choices.all { it.playlist.isEditable })
        assertNull(WebAudioParser.playlistChoices("addLangKeys({})", json))
    }

    @Test
    fun `lyrics fall back to ugc when text is null`() {
        val lyrics = requireNotNull(WebAudioParser.lyrics(json.parseToJsonElement("""{"lyrics":{"text":null,"ugc":["one","two"]}}""")))
        assertFalse(lyrics.isSynced)
        assertEquals(listOf("one", "two"), lyrics.lines.map { it.text })
    }

    @Test
    fun `lyrics accept text as one string and fractional begin`() {
        val plain = requireNotNull(WebAudioParser.lyrics(json.parseToJsonElement("""{"lyrics":{"text":"one<br>two"}}""")))
        assertEquals(listOf("one", "two"), plain.lines.map { it.text })
        val timed = requireNotNull(WebAudioParser.lyrics(json.parseToJsonElement("""{"lyrics":{"timestamps":[{"begin":"1500.0","line":"x"}]}}""")))
        assertEquals(1500L, timed.lines.single().startMs)
        assertNull(WebAudioParser.lyrics(json.parseToJsonElement("""{"lyrics":{"text":[]}}""")))
    }

    @Test
    fun `html catalog rows are read from data-audio`() {
        val html = """<div class="audio_row" data-audio="[1,2,&quot;&quot;,&quot;T&quot;,&quot;P&quot;,60]" data-x="1"></div>"""
        val tracks = WebAudioParser.tracksFromHtml(html, json, 42)
        assertEquals(listOf("2_1"), tracks.map { it.id })
        assertEquals("T", tracks.single().title)
    }
}
