package com.vkmusic.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoadState<T>(
    val data: T,
    val isLoading: Boolean = false,
    val error: String? = null,
)

abstract class TrackSourceViewModel : ViewModel() {

    private val _state = MutableStateFlow(LoadState(emptyList<Track>(), isLoading = true))
    val state: StateFlow<LoadState<List<Track>>> = _state.asStateFlow()

    private var job: Job? = null

    protected abstract suspend fun load(): List<Track>

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { load() }
                .onSuccess { tracks -> _state.value = LoadState(tracks) }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки") } }
        }
    }
}
