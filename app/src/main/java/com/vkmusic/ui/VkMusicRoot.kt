package com.vkmusic.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.AuthMode
import com.vkmusic.ui.navigation.ArtistRoute
import com.vkmusic.ui.navigation.CommunitiesRoute
import com.vkmusic.ui.navigation.ExploreRoute
import com.vkmusic.ui.navigation.FriendsRoute
import com.vkmusic.ui.navigation.HomeRoute
import com.vkmusic.ui.navigation.LocalNavigator
import com.vkmusic.ui.navigation.LoginRoute
import com.vkmusic.ui.navigation.MusicRoute
import com.vkmusic.ui.navigation.Navigator
import com.vkmusic.ui.navigation.OwnerRoute
import com.vkmusic.ui.navigation.PlayerRoute
import com.vkmusic.ui.navigation.PlaylistRoute
import com.vkmusic.ui.navigation.SettingsRoute
import com.vkmusic.ui.navigation.SimilarRoute
import com.vkmusic.ui.navigation.TopLevelDestination
import com.vkmusic.ui.screens.artist.ArtistScreen
import com.vkmusic.ui.screens.friends.CommunitiesScreen
import com.vkmusic.ui.screens.friends.FriendsScreen
import com.vkmusic.ui.screens.friends.OwnerScreen
import com.vkmusic.ui.screens.home.HomeScreen
import com.vkmusic.ui.screens.login.LoginScreen
import com.vkmusic.ui.screens.music.MusicScreen
import com.vkmusic.ui.screens.player.MiniPlayer
import com.vkmusic.ui.screens.player.PlayerScreen
import com.vkmusic.ui.screens.playlists.PlaylistScreen
import com.vkmusic.ui.screens.search.ExploreScreen
import com.vkmusic.ui.screens.settings.SettingsScreen
import com.vkmusic.ui.screens.similar.SimilarScreen
import com.vkmusic.ui.theme.VkMusicTheme

@Composable
fun VkMusicRoot(viewModel: RootViewModel = hiltViewModel()) {
    val rootState by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val current = rootState ?: return
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.exportEvents.collect { snackbarHostState.showSnackbar(it) } }

    VkMusicTheme(themeId = current.settings.themeId) {
        SystemBarsAppearance()
        val startDestination: Any = remember { if (current.authMode == AuthMode.SignedOut) LoginRoute else HomeRoute }
        val navController = rememberNavController()
        val navigator = remember(navController) { NavControllerNavigator(navController) }
        val backStackEntry by navController.currentBackStackEntryAsState()
        val destination = backStackEntry?.destination
        val showChrome = destination != null &&
            !destination.hasRoute<LoginRoute>() &&
            !destination.hasRoute<PlayerRoute>()
        val topLevel = TopLevelDestination.entries.firstOrNull { destination.isOn(it) }

        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState, LocalNavigator provides navigator) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (showChrome) {
                        Column {
                            player?.let {
                                MiniPlayer(
                                    player = it,
                                    onClick = navigator::openPlayer,
                                    modifier = if (topLevel == null) Modifier.navigationBarsPadding() else Modifier,
                                )
                            }
                            if (topLevel != null) MeridiusNavigationBar(topLevel) { navController.navigateTopLevel(it) }
                        }
                    }
                },
            ) { padding ->
                NavHost(navController = navController, startDestination = startDestination) {
                    composable<LoginRoute> {
                        LoginScreen(onSignedIn = { navController.navigateClearingStack(HomeRoute) })
                    }
                    composable<HomeRoute> { HomeScreen(contentPadding = padding) }
                    composable<MusicRoute> { MusicScreen(contentPadding = padding) }
                    composable<ExploreRoute> { ExploreScreen(contentPadding = padding) }
                    composable<FriendsRoute> { FriendsScreen(contentPadding = padding) }
                    composable<CommunitiesRoute> { CommunitiesScreen(contentPadding = padding) }
                    composable<PlaylistRoute> { PlaylistScreen(contentPadding = padding) }
                    composable<OwnerRoute> { OwnerScreen(contentPadding = padding) }
                    composable<SimilarRoute> { SimilarScreen(contentPadding = padding) }
                    composable<ArtistRoute> { ArtistScreen(contentPadding = padding) }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            contentPadding = padding,
                            onSignOut = {
                                viewModel.signOut()
                                navController.navigateClearingStack(LoginRoute)
                                TopLevelDestination.entries.forEach { navController.clearBackStack(it.route) }
                            },
                        )
                    }
                    composable<PlayerRoute> { PlayerScreen(player = player, onBack = navController::popBackStack) }
                }
            }
        }
    }
}

@Composable
private fun MeridiusNavigationBar(selected: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(containerColor = colors.surfaceContainer) {
        TopLevelDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = item == selected,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.primary,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = colors.primary.copy(alpha = 0.16f),
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}

// Светлые темы Meridius (VK White, Higurashi) требуют тёмных значков в статус-баре независимо от темы системы.
@Composable
private fun SystemBarsAppearance() {
    val view = LocalView.current
    val lightBackground = MaterialTheme.colorScheme.background.luminance() > 0.5f
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
    }
}

private class NavControllerNavigator(private val navController: NavHostController) : Navigator {
    override fun openSimilar(track: Track) = navController.navigate(SimilarRoute(track.ownerId, track.audioId, track.title))

    override fun openArtist(artistId: String, name: String) = navController.navigate(ArtistRoute(artistId, name))

    override fun openPlaylist(playlist: Playlist) =
        navController.navigate(PlaylistRoute(playlist.ownerId, playlist.playlistId, playlist.title, playlist.accessKey))

    override fun openOwner(ownerId: Long, name: String) = navController.navigate(OwnerRoute(ownerId, name))

    override fun openPlayer() = navController.navigate(PlayerRoute) { launchSingleTop = true }

    override fun openSettings() = navController.navigate(SettingsRoute) { launchSingleTop = true }

    override fun back() {
        navController.popBackStack()
    }
}

private fun NavDestination?.isOn(item: TopLevelDestination) =
    this?.hierarchy?.any { it.hasRoute(item.routeClass) } == true

private fun NavHostController.navigateTopLevel(item: TopLevelDestination) = navigate(item.route) {
    popUpTo<HomeRoute> { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun <T : Any> NavHostController.navigateClearingStack(route: T) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
}
