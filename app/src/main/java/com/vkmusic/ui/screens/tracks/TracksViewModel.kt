package com.vkmusic.ui.screens.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.TrackRepository
import com.vkmusic.ui.components.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TracksViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<LoadState<List<Track>>> =
        combine(trackRepository.library, refreshing, error) { tracks, isRefreshing, error ->
            LoadState(tracks, isRefreshing, error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState(emptyList(), isLoading = true))

    init {
        refresh()
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            error.value = runCatching { trackRepository.refreshLibrary() }.exceptionOrNull()?.message
            refreshing.value = false
        }
    }
}
