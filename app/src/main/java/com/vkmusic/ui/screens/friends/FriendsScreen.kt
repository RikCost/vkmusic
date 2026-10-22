package com.vkmusic.ui.screens.friends

import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.remember
import com.vkmusic.ui.components.normalizedForSearch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vkmusic.ui.components.AccentTabs
import com.vkmusic.ui.components.Artwork
import com.vkmusic.ui.components.BackTopBar
import com.vkmusic.ui.components.FilterField
import com.vkmusic.ui.components.LoadingBox
import com.vkmusic.ui.components.MessageBox
import com.vkmusic.ui.components.PageTopBar
import com.vkmusic.ui.components.TrackSourcePage
import com.vkmusic.ui.navigation.LocalNavigator
import com.vkmusic.ui.screens.bottomOnly
import com.vkmusic.ui.screens.music.PlaylistRow

@Composable
fun FriendsScreen(contentPadding: PaddingValues, viewModel: FriendsViewModel = hiltViewModel()) {
    OwnersPage("Друзья", "Нет друзей с открытой музыкой", contentPadding, viewModel)
}

@Composable
fun CommunitiesScreen(contentPadding: PaddingValues, viewModel: CommunitiesViewModel = hiltViewModel()) {
    OwnersPage("Сообщества", "Вы не состоите в сообществах", contentPadding, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnersPage(title: String, emptyText: String, contentPadding: PaddingValues, viewModel: OwnersViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    var filter by rememberSaveable { mutableStateOf("") }
    val items = remember(state.data, filter) {
        val query = filter.normalizedForSearch()
        state.data.filter { query.isEmpty() || it.name.normalizedForSearch().contains(query) }
    }

    Column(Modifier.fillMaxSize()) {
        PageTopBar(title)
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.data.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.bottomOnly()) {
                if (state.data.size > FILTER_THRESHOLD) item { FilterField(filter, { filter = it }, placeholder = "Поиск") }
                when {
                    state.error != null -> item { MessageBox(state.error.orEmpty(), icon = Icons.Rounded.CloudOff, onRetry = viewModel::refresh) }
                    state.isLoading && state.data.isEmpty() -> item { LoadingBox() }
                    state.data.isEmpty() -> item { MessageBox(emptyText) }
                    items.isEmpty() -> item { MessageBox("Ничего не найдено", icon = Icons.Rounded.SearchOff) }
                }
                items(items, key = { it.ownerId }) { owner ->
                    ListItem(
                        headlineContent = { Text(owner.name, style = MaterialTheme.typography.titleSmall) },
                        supportingContent = owner.subtitle?.let { subtitle -> @Composable { Text(subtitle) } },
                        leadingContent = {
                            Artwork(owner.photoUrl, owner.ownerId.toString(), Modifier.size(48.dp).clip(CircleShape))
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.clickable { navigator.openOwner(owner.ownerId, owner.name) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerScreen(contentPadding: PaddingValues, viewModel: OwnerTracksViewModel = hiltViewModel()) {
    val navigator = LocalNavigator.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = viewModel.route.name, onBack = navigator::back, subtitle = if (viewModel.route.ownerId < 0) "Сообщество" else "Пользователь")
        AccentTabs(listOf("Музыка", "Плейлисты"), tab) { tab = it }
        Box(Modifier.weight(1f)) {
            if (tab == 0) {
                TrackSourcePage(
                    state = state,
                    onRefresh = viewModel::refresh,
                    contentPadding = contentPadding.bottomOnly(),
                    emptyText = "Музыка скрыта или пуста",
                    filter = filter,
                    header = { if (state.data.isNotEmpty()) item { FilterField(filter, { filter = it }) } },
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = playlists.isLoading && playlists.data.isNotEmpty(),
                    onRefresh = viewModel::refreshPlaylists,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.bottomOnly()) {
                        when {
                            playlists.error != null -> item { MessageBox(playlists.error.orEmpty(), icon = Icons.Rounded.CloudOff, onRetry = viewModel::refreshPlaylists) }
                            playlists.isLoading && playlists.data.isEmpty() -> item { LoadingBox() }
                            playlists.data.isEmpty() -> item { MessageBox("Плейлистов нет") }
                        }
                        items(playlists.data, key = { it.id }) { PlaylistRow(it, navigator::openPlaylist) }
                    }
                }
            }
        }
    }
}

private const val FILTER_THRESHOLD = 8
