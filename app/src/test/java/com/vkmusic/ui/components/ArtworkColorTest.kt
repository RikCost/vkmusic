package com.vkmusic.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkColorTest {

    @Test
    fun `dominant hue wins over black background`() {
        // Тёмная обложка с красным рисунком: фон плеера должен быть красным, а не серым.
        val pixels = IntArray(100) { if (it < 20) 0xFFE00020.toInt() else 0xFF050505.toInt() }
        val color = requireNotNull(dominantColor(pixels))
        assertTrue(color.hue > 345f || color.hue < 15f)
    }

    @Test
    fun `more saturated colour beats a larger pale area`() {
        val pixels = IntArray(100) { if (it < 40) 0xFF0040FF.toInt() else 0xFFE8E0D8.toInt() }
        assertEquals(225f, requireNotNull(dominantColor(pixels)).hue, 5f)
    }

    @Test
    fun `greyscale cover has no colour`() {
        assertNull(dominantColor(IntArray(64) { if (it % 2 == 0) 0xFF202020.toInt() else 0xFFD0D0D0.toInt() }))
    }
}
