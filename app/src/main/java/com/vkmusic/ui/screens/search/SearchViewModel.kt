package com.vkmusic.ui.screens.search

import kotlinx.coroutines.flow.combine
import com.vkmusic.data.toUserMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

data class SearchUiState(
    val tracks: List<Track> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val recommendations: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isQueryEmpty: Boolean = true,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: VkAudioRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val retries = MutableStateFlow(0)

    // Пока грузится новый запрос, на экране остаются прежние результаты — без мигания пустым экраном.
    private var lastResult = SearchUiState(isLoading = true)

    val state: StateFlow<SearchUiState> = _query
        .map { it.trim() }
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else DEBOUNCE_MS }
        .combine(retries) { q, _ -> q }
        .transformLatest { q ->
            val keep = lastResult.takeIf { it.isQueryEmpty == q.isEmpty() && it.error == null } ?: SearchUiState(isQueryEmpty = q.isEmpty())
            emit(keep.copy(isLoading = true, error = null))
            emit(load(q).also { lastResult = it })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(isLoading = true))

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun retry() {
        retries.value++
    }

    private suspend fun load(query: String): SearchUiState = runCatching {
        if (query.isEmpty()) {
            SearchUiState(recommendations = api.getRecommendations())
        } else {
            coroutineScope {
                val tracks = async { api.search(query) }
                val albums = async { api.searchAlbums(query) }
                SearchUiState(tracks = tracks.await(), albums = albums.await(), isQueryEmpty = false)
            }
        }
    }.getOrElse { SearchUiState(error = it.toUserMessage("Ошибка поиска"), isQueryEmpty = query.isEmpty()) }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
