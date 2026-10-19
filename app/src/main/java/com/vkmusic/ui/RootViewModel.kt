package com.vkmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.repository.AppSettings
import com.vkmusic.data.repository.AuthMode
import com.vkmusic.data.repository.AuthRepository
import com.vkmusic.data.repository.SettingsRepository
import com.vkmusic.player.queue.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootState(val settings: AppSettings, val authMode: AuthMode)

@HiltViewModel
class RootViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    val state: StateFlow<RootState?> = combine(settingsRepository.settings, authRepository.mode, ::RootState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val player = playerConnection.player

    fun signOut() {
        player.value?.run {
            stop()
            clearMediaItems()
        }
        viewModelScope.launch { authRepository.signOut() }
    }
}
