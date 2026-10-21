package com.vkmusic.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vkmusic.ui.components.AccentTabs
import com.vkmusic.ui.components.LoadingBox
import com.vkmusic.ui.components.MessageBox
import com.vkmusic.ui.components.SectionTitle
import com.vkmusic.ui.components.TrackList
import com.vkmusic.ui.navigation.LocalNavigator
import com.vkmusic.ui.screens.bottomOnly
import com.vkmusic.ui.screens.music.PlaylistRow

@Composable
fun ExploreScreen(
    contentPadding: PaddingValues,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Обзор", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        TextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Поиск") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Очистить")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.isQueryEmpty) {
            TrackList(
                tracks = state.recommendations,
                contentPadding = contentPadding.bottomOnly(),
                header = {
                    when {
                        state.isLoading -> item { LoadingBox() }
                        state.error != null -> item { MessageBox(state.error.orEmpty()) }
                        else -> item { SectionTitle("Рекомендации") }
                    }
                },
            )
            return@Column
        }
        AccentTabs(listOf("Треки", "Альбомы"), tab) { tab = it }
        when {
            state.isLoading -> LoadingBox()
            state.error != null -> MessageBox(state.error.orEmpty())
            tab == 0 -> TrackList(
                tracks = state.tracks,
                contentPadding = contentPadding.bottomOnly(),
                header = { if (state.tracks.isEmpty()) item { MessageBox("Треки не найдены") } },
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.bottomOnly()) {
                if (state.albums.isEmpty()) item { MessageBox("Альбомы не найдены") }
                items(state.albums, key = { it.id }) { PlaylistRow(it, navigator::openPlaylist) }
            }
        }
    }
}
