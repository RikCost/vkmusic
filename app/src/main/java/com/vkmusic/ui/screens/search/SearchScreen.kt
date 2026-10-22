package com.vkmusic.ui.screens.search

import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.LinearProgressIndicator
import com.vkmusic.ui.components.ListSkeleton
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
import com.vkmusic.ui.components.IconButton
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
                        state.error != null -> item { MessageBox(state.error.orEmpty(), icon = Icons.Rounded.CloudOff, onRetry = viewModel::retry) }
                        state.isLoading && state.recommendations.isEmpty() -> item { ListSkeleton() }
                        else -> item { SectionTitle("Рекомендации") }
                    }
                },
            )
            return@Column
        }
        AccentTabs(listOf("Треки", "Альбомы"), tab) { tab = it }
        // Новый запрос грузится поверх прежних результатов: тонкая полоска вместо пустого экрана.
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            state.error != null -> MessageBox(state.error.orEmpty(), icon = Icons.Rounded.CloudOff, onRetry = viewModel::retry)
            state.isLoading && state.tracks.isEmpty() && state.albums.isEmpty() -> ListSkeleton()
            tab == 0 -> TrackList(
                tracks = state.tracks,
                contentPadding = contentPadding.bottomOnly(),
                header = { if (state.tracks.isEmpty() && !state.isLoading) item { MessageBox("Треки не найдены", icon = Icons.Rounded.SearchOff) } },
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.bottomOnly()) {
                if (state.albums.isEmpty() && !state.isLoading) item { MessageBox("Альбомы не найдены", icon = Icons.Rounded.SearchOff) }
                items(state.albums, key = { it.id }) { PlaylistRow(it, navigator::openPlaylist) }
            }
        }
    }
}
