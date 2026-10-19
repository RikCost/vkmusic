package com.vkmusic.data.repository

import com.vkmusic.data.api.vk.AuthResult
import com.vkmusic.data.api.vk.CaptchaAnswer
import com.vkmusic.data.api.vk.VkAuthApi
import com.vkmusic.data.api.vk.OtpChannel
import com.vkmusic.data.api.vk.OtpResult
import com.vkmusic.data.api.vk.VkCredentials
import com.vkmusic.data.auth.SessionStore
import com.vkmusic.data.auth.VkSession
import com.vkmusic.data.db.TrackDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthMode { SignedOut, Demo, Vk }

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: VkAuthApi,
    private val sessionStore: SessionStore,
    private val settingsRepository: SettingsRepository,
    private val trackDao: TrackDao,
    private val playlistRepository: PlaylistRepository,
) {
    val mode: Flow<AuthMode> = combine(sessionStore.session, settingsRepository.settings) { session, settings ->
        when {
            session != null -> AuthMode.Vk
            settings.isDemoMode -> AuthMode.Demo
            else -> AuthMode.SignedOut
        }
    }

    suspend fun login(
        credentials: VkCredentials,
        code: String? = null,
        captcha: CaptchaAnswer? = null,
        sid: String? = null,
        forceSms: Boolean = false,
    ): AuthResult {
        val result = authApi.login(credentials, code, captcha, sid, forceSms)
        if (result is AuthResult.Success) {
            trackDao.clear()
            settingsRepository.setDemoMode(false)
            sessionStore.save(VkSession(result.accessToken, result.userId))
            playlistRepository.clear()
        }
        return result
    }

    suspend fun sendOtp(sid: String, channel: OtpChannel): OtpResult = authApi.sendOtp(sid, channel)

    suspend fun enterDemoMode() {
        trackDao.clear()
        settingsRepository.setDemoMode(true)
    }

    suspend fun signOut() {
        sessionStore.clear()
        settingsRepository.setDemoMode(false)
        trackDao.clear()
        playlistRepository.clear()
    }
}
