package com.vkmusic.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track

/** Переходы, доступные из любого списка треков и плейлистов (меню трека, карточки). */
interface Navigator {
    fun openSimilar(track: Track)
    fun openArtist(artistId: String, name: String)

    /** Обзор с уже введённым запросом — например, поиск исполнителя из плеера. */
    fun openSearch(query: String)
    fun openPlaylist(playlist: Playlist)
    fun openOwner(ownerId: Long, name: String)
    fun openPlayer()
    fun openSettings()
    fun back()
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator не предоставлен") }
