package com.vkmusic.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.reflect.KClass

enum class TopLevelDestination(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector) {
    Music(TracksRoute, TracksRoute::class, "Музыка", Icons.Rounded.LibraryMusic),
    Search(SearchRoute, SearchRoute::class, "Поиск", Icons.Rounded.Search),
    Playlists(PlaylistsRoute, PlaylistsRoute::class, "Плейлисты", Icons.AutoMirrored.Rounded.QueueMusic),
    Friends(FriendsRoute, FriendsRoute::class, "Друзья", Icons.Rounded.People),
    Downloads(DownloadsRoute, DownloadsRoute::class, "Загрузки", Icons.Rounded.DownloadForOffline),
}
