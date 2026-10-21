package com.vkmusic.player.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class CrossfadeCurveTest {

    @Test
    fun `starts with outgoing track and ends with incoming`() {
        assertEquals(1f to 0f, CrossfadeCurve.gains(0f).round())
        assertEquals(0f to 1f, CrossfadeCurve.gains(1f).round())
    }

    @Test
    fun `keeps total power constant`() {
        for (step in 0..20) {
            val (out, `in`) = CrossfadeCurve.gains(step / 20f)
            assertEquals(1f, out * out + `in` * `in`, 1e-5f)
        }
    }

    @Test
    fun `clamps progress outside of range`() {
        assertEquals(CrossfadeCurve.gains(0f), CrossfadeCurve.gains(-0.5f))
        assertEquals(CrossfadeCurve.gains(1f), CrossfadeCurve.gains(3f))
        val (out, `in`) = CrossfadeCurve.gains(0.5f)
        assertEquals(out, `in`, 1e-6f)
    }

    private fun Pair<Float, Float>.round() = Math.round(first * 1000) / 1000f to Math.round(second * 1000) / 1000f
}
