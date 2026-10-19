package com.vkmusic.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute

@Serializable
data object TracksRoute

@Serializable
data object SearchRoute

@Serializable
data object PlaylistsRoute

@Serializable
data object FriendsRoute

@Serializable
data object DownloadsRoute

@Serializable
data object PlayerRoute

@Serializable
data class PlaylistRoute(val ownerId: Long, val playlistId: Long, val title: String, val accessKey: String? = null)

@Serializable
data class FriendRoute(val ownerId: Long, val name: String)

@Serializable
data class SimilarRoute(val ownerId: Long, val audioId: Long, val title: String)
