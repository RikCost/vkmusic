package com.vkmusic.player.service

import androidx.media3.common.C
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.UrlResolution
import com.vkmusic.player.queue.isConnectionProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkResilienceTest {

    @Test
    fun `lost connection is told apart from a broken track`() {
        assertTrue(IOException("open", UnknownHostException("vk.ru")).isConnectionProblem())
        assertTrue(IOException("read", SocketTimeoutException()).isConnectionProblem())
        assertTrue(SocketException("Connection reset").isConnectionProblem())
        assertFalse(IOException("bad data").isConnectionProblem())
        assertFalse(IllegalStateException("decoder").isConnectionProblem())
    }

    @Test
    fun `offline load is retried forever with a capped pause`() {
        val noNetwork = IOException(UnknownHostException())
        assertEquals(1_000L, patientRetryDelay(noNetwork, attempt = 1, online = false))
        assertEquals(5_000L, patientRetryDelay(noNetwork, attempt = 10, online = false))
        assertEquals(5_000L, patientRetryDelay(noNetwork, attempt = 10_000, online = false))
    }

    @Test
    fun `with network the connection error gives up after a few retries`() {
        val timeout = IOException(SocketTimeoutException())
        assertEquals(3_000L, patientRetryDelay(timeout, attempt = 3, online = true))
        assertEquals(C.TIME_UNSET, patientRetryDelay(timeout, attempt = 7, online = true))
    }

    @Test
    fun `other errors keep the default policy but stop after three failures`() {
        val other = IOException("bad data")
        assertNull(patientRetryDelay(other, attempt = 1, online = true))
        assertEquals(C.TIME_UNSET, patientRetryDelay(other, attempt = 4, online = false))
    }

    @Test
    fun `track without link after a successful request is unavailable`() {
        val tracks = listOf(track(1, url = "https://cs.vk/1.mp3"), track(2, url = null))
        assertEquals(setOf("1_2"), UrlResolution(tracks, requestFailed = false).unavailableIds)
        // Запрос не прошёл (нет сети): о доступности ничего не известно.
        assertTrue(UrlResolution(tracks, requestFailed = true).unavailableIds.isEmpty())
    }

    private fun track(id: Long, url: String?) =
        Track(ownerId = 1, audioId = id, title = "t$id", artist = "a", durationSec = 1, url = url, webHashes = "a/b/c/d/e/f")
}
