package com.vkmusic.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.reflect.KClass

// Навигация Meridius (langs/ru.json → NAVIGATION): Главная, Музыка, Обзор, Друзья, Сообщества.
enum class TopLevelDestination(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector) {
    Home(HomeRoute, HomeRoute::class, "Главная", Icons.Rounded.Home),
    Music(MusicRoute, MusicRoute::class, "Музыка", Icons.Rounded.MusicNote),
    Explore(ExploreRoute, ExploreRoute::class, "Обзор", Icons.Rounded.Tag),
    Friends(FriendsRoute, FriendsRoute::class, "Друзья", Icons.Rounded.People),
    Communities(CommunitiesRoute, CommunitiesRoute::class, "Сообщества", Icons.Rounded.Groups),
}
