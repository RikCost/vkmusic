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
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthMode { SignedOut, Vk }

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
    private val trackDao: TrackDao,
    private val playlistRepository: PlaylistRepository,
) {
    val mode: Flow<AuthMode> = sessionStore.session.map { if (it != null) AuthMode.Vk else AuthMode.SignedOut }

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

    suspend fun signOut() {
        webSession.signOut()
        trackDao.clear()
        playlistRepository.clear()
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
    }

    private suspend fun finishLogin() {
        trackDao.clear()
        playlistRepository.clear()
        webSession.completeLogin()
    }
}
