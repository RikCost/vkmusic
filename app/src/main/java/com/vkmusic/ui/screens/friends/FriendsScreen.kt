package com.vkmusic.ui.screens.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vkmusic.data.model.Friend
import com.vkmusic.data.model.Track
import com.vkmusic.ui.components.Artwork
import com.vkmusic.ui.components.BackTopBar
import com.vkmusic.ui.components.LoadingBox
import com.vkmusic.ui.components.MessageBox
import com.vkmusic.ui.components.TrackSourcePage
import com.vkmusic.ui.screens.bottomOnly

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    contentPadding: PaddingValues,
    onOpenFriend: (Friend) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Музыка друзей") })
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.data.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.bottomOnly()) {
                when {
                    state.error != null -> item { MessageBox(state.error.orEmpty()) }
                    state.isLoading && state.data.isEmpty() -> item { LoadingBox() }
                    state.data.isEmpty() -> item { MessageBox("Нет друзей с открытой музыкой") }
                }
                items(state.data, key = { it.id }) { friend ->
                    ListItem(
                        headlineContent = { Text(friend.name) },
                        supportingContent = { if (friend.trackCount > 0) Text("${friend.trackCount} треков") },
                        leadingContent = {
                            Artwork(friend.photoUrl, friend.id.toString(), Modifier.size(48.dp).clip(CircleShape))
                        },
                        modifier = Modifier.clickable { onOpenFriend(friend) },
                    )
                }
            }
        }
    }
}

@Composable
fun FriendTracksScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSimilar: (Track) -> Unit,
    viewModel: FriendTracksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = viewModel.route.name, onBack = onBack, subtitle = "Музыка")
        TrackSourcePage(
            state = state,
            onRefresh = viewModel::refresh,
            onSimilar = onSimilar,
            contentPadding = contentPadding.bottomOnly(),
            emptyText = "Музыка скрыта или пуста",
        )
    }
}
