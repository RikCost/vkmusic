package com.vkmusic.data.api.vk

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class VkApiClientTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val server = MockWebServer()
    private lateinit var repository: VkApiAudioRepository

    @Before
    fun setUp() {
        server.start()
        val client = VkApiClient(OkHttpClient(), json, tokenProvider = { "test-token" }, apiUrl = server.url("/method/"))
        repository = VkApiAudioRepository(client) { 42L }
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `audio get sends token version and user agent and maps tracks`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """
                {"response":{"count":3,"items":[
                  {"id":1,"owner_id":42,"artist":"A","title":"Hls","duration":200,
                   "url":"https://cs1.vkuseraudio.net/s/v1/ac/x/index.m3u8?extra=1",
                   "album":{"id":7,"thumb":{"photo_300":"https://img/300.jpg","photo_600":"https://img/600.jpg"}}},
                  {"id":2,"owner_id":42,"artist":"B","title":"Mp3","duration":100,"url":"https://cs1.vkuseraudio.net/a.mp3"},
                  {"id":3,"owner_id":42,"artist":"C","title":"Blocked","duration":90,"url":"https://vk.com/mp3/audio_api_unavailable.mp3"}
                ]}}
                """.trimIndent(),
            ).build(),
        )

        val tracks = repository.getMyAudio()

        val request = server.takeRequest()
        val body = requireNotNull(request.body).utf8()
        assertEquals("/method/audio.get", request.url.encodedPath)
        assertEquals(VkConfig.USER_AGENT, request.headers["User-Agent"])
        assertEquals(VkConfig.ANDROID_CLIENT_VALUE, request.headers[VkConfig.ANDROID_CLIENT_HEADER])
        assertTrue(body.contains("access_token=test-token"))
        assertTrue(body.contains("v=${VkConfig.API_VERSION}"))
        assertTrue(body.contains("owner_id=42"))

        assertEquals(3, tracks.size)
        assertTrue(tracks[0].isHls)
        assertEquals("https://img/600.jpg", tracks[0].coverUrl)
        assertEquals("42_1", tracks[0].id)
        assertFalse(tracks[1].isHls)
        assertNull(tracks[2].url)
    }

    @Test
    fun `api error is thrown as VkApiException`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .body("""{"error":{"error_code":15,"error_msg":"Access denied: token required","request_params":[]}}""")
                .build(),
        )
        try {
            repository.getRecommendations()
            fail("Expected VkApiException")
        } catch (e: VkApiException) {
            assertEquals(15, e.code)
            assertEquals("Access denied: token required", e.message)
        }
    }

    @Test
    fun `auth error is detected`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"error":{"error_code":5,"error_msg":"User authorization failed"}}""").build())
        try {
            repository.getFriends()
            fail("Expected VkApiException")
        } catch (e: VkApiException) {
            assertTrue(e.isAuthError)
        }
    }

    @Test
    fun `similar uses target audio and playlists map editable and albums`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"response":{"count":0,"items":[]}}""").build())
        repository.getSimilar(-1, 5)
        assertTrue(requireNotNull(server.takeRequest().body).utf8().contains("target_audio=-1_5"))

        server.enqueue(
            MockResponse.Builder().body(
                """
                {"response":{"count":3,"items":[
                  {"id":10,"owner_id":42,"title":"Мой","count":3,"access_key":"k1"},
                  {"id":11,"owner_id":42,"title":"Чужой","count":5,"original":{"id":99,"owner_id":7,"access_key":"k2"}},
                  {"id":12,"owner_id":-5,"title":"Альбом","count":9,"main_artists":[{"name":"Artist"}],
                   "photo":{"photo_300":"https://img/a.jpg"}}
                ]}}
                """.trimIndent(),
            ).build(),
        )
        val playlists = repository.getMyPlaylists()

        assertTrue(playlists[0].isEditable)
        assertFalse(playlists[0].isAlbum)
        assertFalse(playlists[1].isEditable)
        assertEquals(7L, playlists[1].ownerId)
        assertEquals(99L, playlists[1].playlistId)
        assertEquals("k2", playlists[1].accessKey)
        assertTrue(playlists[2].isAlbum)
        assertEquals("Artist", playlists[2].subtitle)
        assertEquals("https://img/a.jpg", playlists[2].coverUrl)
    }

    @Test
    fun `missing token fails without network call`() = runTest {
        val client = VkApiClient(OkHttpClient(), json, tokenProvider = { null }, apiUrl = server.url("/method/"))
        try {
            client.call("audio.get", kotlinx.serialization.json.JsonElement.serializer())
            fail("Expected VkApiException")
        } catch (e: VkApiException) {
            assertTrue(e.isAuthError)
        }
        assertEquals(0, server.requestCount)
    }
}
