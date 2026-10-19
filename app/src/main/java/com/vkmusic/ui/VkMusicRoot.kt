package com.vkmusic.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.AuthMode
import com.vkmusic.ui.navigation.DownloadsRoute
import com.vkmusic.ui.navigation.FriendRoute
import com.vkmusic.ui.navigation.FriendsRoute
import com.vkmusic.ui.navigation.LoginRoute
import com.vkmusic.ui.navigation.PlayerRoute
import com.vkmusic.ui.navigation.PlaylistRoute
import com.vkmusic.ui.navigation.PlaylistsRoute
import com.vkmusic.ui.navigation.SearchRoute
import com.vkmusic.ui.navigation.SimilarRoute
import com.vkmusic.ui.navigation.TopLevelDestination
import com.vkmusic.ui.navigation.TracksRoute
import com.vkmusic.ui.screens.downloads.DownloadsScreen
import com.vkmusic.ui.screens.friends.FriendTracksScreen
import com.vkmusic.ui.screens.friends.FriendsScreen
import com.vkmusic.ui.screens.login.LoginScreen
import com.vkmusic.ui.screens.player.MiniPlayer
import com.vkmusic.ui.screens.player.PlayerScreen
import com.vkmusic.ui.screens.playlists.PlaylistScreen
import com.vkmusic.ui.screens.playlists.PlaylistsScreen
import com.vkmusic.ui.screens.search.SearchScreen
import com.vkmusic.ui.screens.similar.SimilarScreen
import com.vkmusic.ui.screens.tracks.TracksScreen
import com.vkmusic.ui.theme.VkMusicTheme

@Composable
fun VkMusicRoot(viewModel: RootViewModel = hiltViewModel()) {
    val rootState by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val current = rootState ?: return
    val snackbarHostState = remember { SnackbarHostState() }

    VkMusicTheme(dynamicColor = current.settings.dynamicColor) {
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            val startDestination: Any = remember { if (current.authMode == AuthMode.SignedOut) LoginRoute else TracksRoute }
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val destination = backStackEntry?.destination
            val showChrome = destination != null &&
                !destination.hasRoute<LoginRoute>() &&
                !destination.hasRoute<PlayerRoute>()
            val topLevel = TopLevelDestination.entries.firstOrNull { destination.isOn(it) }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (showChrome) {
                        Column {
                            player?.let {
                                MiniPlayer(
                                    player = it,
                                    onClick = { navController.navigate(PlayerRoute) { launchSingleTop = true } },
                                    modifier = if (topLevel == null) Modifier.navigationBarsPadding() else Modifier,
                                )
                            }
                            if (topLevel != null) {
                                NavigationBar {
                                    TopLevelDestination.entries.forEach { item ->
                                        NavigationBarItem(
                                            selected = item == topLevel,
                                            onClick = { navController.navigateTopLevel(item) },
                                            icon = { Icon(item.icon, contentDescription = null) },
                                            label = { Text(item.label) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                val openSimilar: (Track) -> Unit = {
                    navController.navigate(SimilarRoute(it.ownerId, it.audioId, it.title))
                }
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                ) {
                    composable<LoginRoute> {
                        LoginScreen(onSignedIn = { navController.navigateClearingStack(TracksRoute) })
                    }
                    composable<TracksRoute> {
                        TracksScreen(
                            contentPadding = padding,
                            onSimilar = openSimilar,
                            onSignOut = {
                                viewModel.signOut()
                                navController.navigateClearingStack(LoginRoute)
                                TopLevelDestination.entries.forEach { navController.clearBackStack(it.route) }
                            },
                        )
                    }
                    composable<SearchRoute> {
                        SearchScreen(
                            contentPadding = padding,
                            onSimilar = openSimilar,
                            onOpenPlaylist = { navController.navigate(PlaylistRoute(it.ownerId, it.playlistId, it.title, it.accessKey)) },
                        )
                    }
                    composable<PlaylistsRoute> {
                        PlaylistsScreen(
                            contentPadding = padding,
                            onOpenPlaylist = { navController.navigate(PlaylistRoute(it.ownerId, it.playlistId, it.title, it.accessKey)) },
                        )
                    }
                    composable<PlaylistRoute> {
                        PlaylistScreen(contentPadding = padding, onBack = navController::popBackStack, onSimilar = openSimilar)
                    }
                    composable<FriendsRoute> {
                        FriendsScreen(
                            contentPadding = padding,
                            onOpenFriend = { navController.navigate(FriendRoute(it.id, it.name)) },
                        )
                    }
                    composable<FriendRoute> {
                        FriendTracksScreen(contentPadding = padding, onBack = navController::popBackStack, onSimilar = openSimilar)
                    }
                    composable<SimilarRoute> {
                        SimilarScreen(contentPadding = padding, onBack = navController::popBackStack, onSimilar = openSimilar)
                    }
                    composable<DownloadsRoute> {
                        DownloadsScreen(contentPadding = padding, onSimilar = openSimilar)
                    }
                    composable<PlayerRoute> {
                        PlayerScreen(player = player, onBack = navController::popBackStack, onSimilar = openSimilar)
                    }
                }
            }
        }
    }
}

private fun NavDestination?.isOn(item: TopLevelDestination) =
    this?.hierarchy?.any { it.hasRoute(item.routeClass) } == true

private fun NavHostController.navigateTopLevel(item: TopLevelDestination) = navigate(item.route) {
    popUpTo<TracksRoute> { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun <T : Any> NavHostController.navigateClearingStack(route: T) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
}
