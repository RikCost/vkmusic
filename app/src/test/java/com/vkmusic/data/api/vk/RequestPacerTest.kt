package com.vkmusic.data.api.vk

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestPacerTest {

    @Test
    fun `burst passes at once, then requests are spaced`() {
        val pacer = RequestPacer(burst = 3, intervalMs = 100)
        assertEquals(listOf(0L, 0L, 0L), List(3) { pacer.reserve(nowMs = 1_000) })
        // Четвёртый и пятый запрос в тот же миг встают в очередь друг за другом.
        assertEquals(100L, pacer.reserve(nowMs = 1_000))
        assertEquals(200L, pacer.reserve(nowMs = 1_000))
    }

    @Test
    fun `idle time refills the burst but not beyond it`() {
        val pacer = RequestPacer(burst = 2, intervalMs = 100)
        repeat(2) { pacer.reserve(nowMs = 0) }
        // За час простоя копится не больше двух запросов без ожидания.
        assertEquals(listOf(0L, 0L, 100L), List(3) { pacer.reserve(nowMs = 3_600_000) })
    }
}
