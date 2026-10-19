package com.vkmusic.ui.screens.playlists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vkmusic.data.model.Track
import com.vkmusic.ui.components.BackTopBar
import com.vkmusic.ui.components.TrackSourcePage
import com.vkmusic.ui.screens.bottomOnly

@Composable
fun PlaylistScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSimilar: (Track) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = viewModel.route.title, onBack = onBack, subtitle = "${state.data.size} треков")
        TrackSourcePage(
            state = state,
            onRefresh = viewModel::refresh,
            onSimilar = onSimilar,
            contentPadding = contentPadding.bottomOnly(),
            emptyText = "В плейлисте пока нет треков",
        )
    }
}
