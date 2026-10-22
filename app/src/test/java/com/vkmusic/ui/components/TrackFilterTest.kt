package com.vkmusic.ui.components

import com.vkmusic.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackFilterTest {

    private val tracks = listOf(
        Track(ownerId = 1, audioId = 1, title = "Ёжик в тумане", artist = "Кто-то", durationSec = 60, url = null),
        Track(ownerId = 1, audioId = 2, title = "Intro", artist = "Band", durationSec = 60, url = null, albumTitle = "Ночной альбом"),
        Track(ownerId = 1, audioId = 3, title = "Other", artist = "Someone", durationSec = 60, url = null),
    )

    @Test
    fun `filter ignores case and treats yo as ye`() {
        assertEquals(listOf("1_1"), tracks.filteredBy("  ежик ").map { it.id })
        assertEquals(listOf("1_1"), tracks.filteredBy("ЁЖИК").map { it.id })
    }

    @Test
    fun `filter searches album titles too`() {
        assertEquals(listOf("1_2"), tracks.filteredBy("ночной").map { it.id })
        assertEquals(tracks, tracks.filteredBy(" "))
    }
}
