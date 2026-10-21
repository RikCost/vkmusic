package com.vkmusic.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute

@Serializable
data object HomeRoute

@Serializable
data object MusicRoute

@Serializable
data object ExploreRoute

@Serializable
data object FriendsRoute

@Serializable
data object CommunitiesRoute

@Serializable
data object PlayerRoute

@Serializable
data object SettingsRoute

@Serializable
data class PlaylistRoute(val ownerId: Long, val playlistId: Long, val title: String, val accessKey: String? = null)

/** Музыка и плейлисты пользователя (ownerId > 0) или сообщества (ownerId < 0). */
@Serializable
data class OwnerRoute(val ownerId: Long, val name: String)

@Serializable
data class SimilarRoute(val ownerId: Long, val audioId: Long, val title: String)

@Serializable
data class ArtistRoute(val artistId: String, val name: String)
