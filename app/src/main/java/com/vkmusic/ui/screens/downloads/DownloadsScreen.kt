package com.vkmusic.ui.screens.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.DownloadRepository
import com.vkmusic.ui.components.LoadState
import com.vkmusic.ui.components.TrackSourcePage
import com.vkmusic.ui.screens.bottomOnly
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    downloadRepository: DownloadRepository,
) : ViewModel() {

    val state: StateFlow<LoadState<List<Track>>> = downloadRepository.downloads
        .map { downloads -> LoadState(downloads.values.sortedByDescending { it.updatedAt }.map { it.track }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState(emptyList()))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    contentPadding: PaddingValues,
    onSimilar: (Track) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Загрузки") })
        TrackSourcePage(
            state = state,
            onRefresh = {},
            onSimilar = onSimilar,
            contentPadding = contentPadding.bottomOnly(),
            emptyText = "Скачанных треков нет. Скачать можно из меню трека или целиком плейлист и альбом.",
        )
    }
}
