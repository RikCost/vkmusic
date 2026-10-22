package com.vkmusic.ui.screens.music

import com.vkmusic.data.toUserMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.TrackRepository
import com.vkmusic.ui.components.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
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
            error.value = refreshWithRetry()?.toUserMessage()
            refreshing.value = false
        }
    }

    // Сразу после запуска сеть может ещё не подняться: пара повторов вместо ошибки и ручного обновления.
    private suspend fun refreshWithRetry(): Throwable? {
        var backoff = RETRY_MIN_MS
        repeat(RETRY_ATTEMPTS) { attempt ->
            val failure = runCatching { trackRepository.refreshLibrary() }.exceptionOrNull() ?: return null
            if (failure !is IOException || attempt == RETRY_ATTEMPTS - 1) return failure
            delay(backoff)
            backoff *= 2
        }
        return null
    }

    private companion object {
        const val RETRY_ATTEMPTS = 3
        const val RETRY_MIN_MS = 2_000L
    }
}
