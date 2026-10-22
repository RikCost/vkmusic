package com.vkmusic.player.queue

import com.vkmusic.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

class PlaybackPolicyTest {

    @Test
    fun `unresolved track is retried, then skipped, then left alone`() {
        for (attempts in 0 until MAX_RESOLVE_ATTEMPTS) {
            assertEquals(UnresolvedAction.RETRY, unresolvedAction(attempts, consecutiveSkips = 0, hasNext = true))
        }
        assertEquals(UnresolvedAction.SKIP, unresolvedAction(MAX_RESOLVE_ATTEMPTS, consecutiveSkips = 0, hasNext = true))
        // Последний трек: пропускать некуда.
        assertEquals(UnresolvedAction.GIVE_UP, unresolvedAction(MAX_RESOLVE_ATTEMPTS, consecutiveSkips = 0, hasNext = false))
        // Подряд не играет ничего — скорее всего, нет сети: очередь не пролистывается до конца.
        assertEquals(UnresolvedAction.GIVE_UP, unresolvedAction(MAX_RESOLVE_ATTEMPTS, MAX_CONSECUTIVE_SKIPS, hasNext = true))
    }

    @Test
    fun `only 403 404 410 count as expired link`() {
        assertTrue(listOf(403, 404, 410).all(::isExpiredLinkResponse))
        assertTrue(listOf(200, 206, 400, 416, 429, 500, 503).none(::isExpiredLinkResponse))
    }

    @Test
    fun `non http errors have no response code`() {
        assertNull(IOException("timeout", IllegalStateException()).httpResponseCode())
    }

    @Test
    fun `link can be refreshed by hashes or access key`() {
        assertTrue(track(url = URL, webHashes = HASHES).canRefreshUrl)
        assertTrue(track(url = URL, accessKey = "key").canRefreshUrl)
        assertFalse(track(url = URL).canRefreshUrl)
        // Ссылки ещё нет — это не обновление, а обычное получение.
        assertFalse(track(url = null, webHashes = HASHES).canRefreshUrl)
    }

    @Test
    fun `restore keeps download urls and drops stale web urls`() {
        val web = track(1, url = "https://old/1.mp3", webHashes = HASHES)
        val downloadedWeb = track(2, url = "https://old/2.m3u8", webHashes = HASHES)
        val api = track(3, url = "https://old/3.mp3")
        val download = downloadedWeb.copy(url = "https://download/2.m3u8", isHls = true)

        val restored = restoredTracks(listOf(web, downloadedWeb, api), mapOf(download.id to download))

        assertNull(restored[0].url)
        assertEquals(download.url, restored[1].url)
        assertTrue(restored[1].isHls)
        assertEquals(api.url, restored[2].url)
    }

    @Test
    fun `fallback starts follow the queue or are random when shuffled`() {
        assertEquals(listOf(3, 4, 5), fallbackStarts(size = 10, start = 2, shuffle = false, count = 3))
        assertEquals(listOf(9), fallbackStarts(size = 10, start = 8, shuffle = false, count = 3))
        assertEquals(emptyList<Int>(), fallbackStarts(size = 1, start = 0, shuffle = false, count = 3))

        val shuffled = fallbackStarts(size = 10, start = 4, shuffle = true, count = 3, random = Random(1))
        assertEquals(3, shuffled.size)
        assertEquals(3, shuffled.toSet().size)
        assertTrue(shuffled.none { it == 4 } && shuffled.all { it in 0 until 10 })
        assertEquals(emptyList<Int>(), fallbackStarts(size = 1, start = 0, shuffle = true, count = 3))
    }

    private fun track(audioId: Long = 1, url: String?, webHashes: String? = null, accessKey: String? = null) = Track(
        ownerId = 1,
        audioId = audioId,
        title = "t$audioId",
        artist = "a",
        durationSec = 180,
        url = url,
        webHashes = webHashes,
        accessKey = accessKey,
    )

    private companion object {
        const val URL = "https://cdn/1.mp3"
        const val HASHES = "a/b/c/d/e/f"
    }
}
