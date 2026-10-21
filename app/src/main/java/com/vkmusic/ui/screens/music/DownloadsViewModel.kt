package com.vkmusic.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.DownloadRepository
import com.vkmusic.ui.components.LoadState
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
