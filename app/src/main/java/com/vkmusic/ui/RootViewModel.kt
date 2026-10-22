package com.vkmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.export.Mp3ExportRepository
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
    mp3ExportRepository: Mp3ExportRepository,
) : ViewModel() {

    /** Итоги сохранения в MP3 — показываются, на каком бы экране ни был пользователь. */
    val exportEvents = mp3ExportRepository.events

    val state: StateFlow<RootState?> = combine(settingsRepository.settings, authRepository.mode, ::RootState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val player = playerConnection.player

    val sessionExpired = authRepository.sessionExpired

    fun signOut() {
        // Сначала очистить очередь: тогда сервис, сохраняющий её при паузе, увидит пустой плеер.
        player.value?.run {
            clearMediaItems()
            stop()
        }
        // Сохранённая очередь принадлежит прошлому аккаунту — её нельзя восстанавливать после входа.
        playerConnection.forgetQueue()
        viewModelScope.launch { authRepository.signOut() }
    }
}
