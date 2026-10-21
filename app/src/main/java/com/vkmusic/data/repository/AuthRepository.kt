package com.vkmusic.data.repository

import android.webkit.CookieManager
import com.vkmusic.data.auth.SessionStore
import com.vkmusic.data.auth.WebSessionManager
import com.vkmusic.data.db.TrackDao
import com.vkmusic.data.web.QrCode
import com.vkmusic.data.web.QrStatus
import com.vkmusic.data.web.VkQrAuth
import com.vkmusic.data.web.VkWebClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthMode { SignedOut, Demo, Vk }

/**
 * Вход как в Meridius — QR-кодом VK ID с подтверждением в приложении VK (и кодом, если VK его
 * попросит). Дополнительно — вход на странице VK во встроенном браузере: его cookie переносятся
 * в ту же веб-сессию. В обоих случаях токен API затем выдаёт login.vk.ru/?act=web_token.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val qrAuth: VkQrAuth,
    private val web: VkWebClient,
    private val webSession: WebSessionManager,
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

    suspend fun requestQr(): QrCode {
        // Новый вход начинается с чистой cookie-сессии, иначе VK ID может подхватить чужой аккаунт.
        web.clearSession()
        return qrAuth.requestQr()
    }

    /** Опрос QR; при подтверждении сессия сразу сохраняется. */
    suspend fun checkQr(): QrStatus {
        val status = qrAuth.checkStatus()
        if (status == QrStatus.Confirmed) finishLogin()
        return status
    }

    suspend fun submitCode(code: String): Boolean = qrAuth.validateCode(code)

    /** Cookie со страниц VK из WebView: адрес → значение заголовка Cookie. */
    suspend fun completeWebViewLogin(cookies: Map<String, String>) {
        web.clearSession()
        cookies.forEach { (url, header) -> web.importCookies(url, header) }
        finishLogin()
    }

    suspend fun enterDemoMode() {
        trackDao.clear()
        settingsRepository.setDemoMode(true)
    }

    suspend fun signOut() {
        webSession.signOut()
        settingsRepository.setDemoMode(false)
        trackDao.clear()
        playlistRepository.clear()
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
    }

    private suspend fun finishLogin() {
        trackDao.clear()
        playlistRepository.clear()
        settingsRepository.setDemoMode(false)
        webSession.completeLogin()
    }
}
