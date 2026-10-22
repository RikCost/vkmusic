package com.vkmusic.ui.screens.home

import com.vkmusic.data.toUserMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.MixCategory
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Profile
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.PlaylistRepository
import com.vkmusic.data.repository.SettingsRepository
import com.vkmusic.player.queue.PlayerConnection
import com.vkmusic.ui.components.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MixState(
    val categories: LoadState<List<MixCategory>> = LoadState(emptyList()),
    val isStarting: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: VkAudioRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _recommendations = MutableStateFlow(LoadState(emptyList<Track>(), isLoading = true))
    val recommendations: StateFlow<LoadState<List<Track>>> = _recommendations.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists

    private val _mix = MutableStateFlow(MixState())
    val mix: StateFlow<MixState> = _mix.asStateFlow()

    val mixSelected: StateFlow<Map<String, List<String>>> = settingsRepository.settings.map { it.mixOptions }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _profile.value = runCatching { api.getProfile() }.getOrNull() }
        viewModelScope.launch { runCatching { playlistRepository.refresh() } }
        viewModelScope.launch {
            _recommendations.update { it.copy(isLoading = true, error = null) }
            // Сбой обновления не стирает уже показанные рекомендации.
            runCatching { api.getRecommendations() }
                .onSuccess { _recommendations.value = LoadState(it) }
                .onFailure { e -> _recommendations.update { it.copy(isLoading = false, error = e.toUserMessage("Ошибка загрузки")) } }
        }
    }

    fun loadMixSettings() {
        val current = _mix.value.categories
        if (current.isLoading || current.data.isNotEmpty()) return
        _mix.update { it.copy(categories = LoadState(emptyList(), isLoading = true)) }
        viewModelScope.launch {
            val result = runCatching { api.getMixSettings() }
                .fold({ LoadState(it, error = if (it.isEmpty()) "Настройки микса недоступны" else null) }, {
                    LoadState(emptyList(), error = it.toUserMessage("Настройки микса недоступны"))
                })
            _mix.update { it.copy(categories = result) }
        }
    }

    fun toggleMixOption(categoryId: String, optionId: String) {
        viewModelScope.launch {
            val selected = settingsRepository.settings.first().mixOptions
            val current = selected[categoryId].orEmpty()
            val updated = if (optionId in current) current - optionId else current + optionId
            settingsRepository.setMixOptions(selected + (categoryId to updated))
        }
    }

    fun resetMix() {
        viewModelScope.launch { settingsRepository.setMixOptions(emptyMap()) }
    }

    // VK Микс Meridius: mix_id=common + выбранные фильтры, очередь бесконечная (подгрузка порциями).
    fun playMix() {
        if (_mix.value.isStarting) return
        _mix.update { it.copy(isStarting = true) }
        viewModelScope.launch {
            val options = settingsRepository.settings.first().mixOptions
            runCatching { playerConnection.playEndless { api.getMix(MIX_COMMON, null, options, MIX_PAGE) } }
                .onSuccess { started -> if (!started) _messages.trySend("Не удалось загрузить треки микса") }
                .onFailure { _messages.trySend(it.toUserMessage("Не удалось загрузить треки микса")) }
            _mix.update { it.copy(isStarting = false) }
        }
    }

    private companion object {
        const val MIX_COMMON = "common"
        const val MIX_PAGE = 10
    }
}
