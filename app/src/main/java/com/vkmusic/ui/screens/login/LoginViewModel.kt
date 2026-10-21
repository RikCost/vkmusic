package com.vkmusic.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.repository.AuthRepository
import com.vkmusic.data.web.QrCode
import com.vkmusic.data.web.QrStatus
import com.vkmusic.data.web.VkWebConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

enum class LoginStep { Qr, Code, WebView, Done }

data class LoginUiState(
    val step: LoginStep = LoginStep.Qr,
    val qr: QrCode? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val codeError: String? = null,
    // Сеть пропала: QR остаётся на экране, запросы повторяются сами.
    val reconnecting: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var webViewJob: Job? = null

    init {
        loadQr()
    }

    /** Новый QR-код (Meridius `generateQR`) и опрос статуса раз в 3 секунды. */
    fun loadQr() {
        pollJob?.cancel()
        _state.update { it.copy(step = LoginStep.Qr, qr = null, isLoading = true, error = null, reconnecting = false) }
        pollJob = viewModelScope.launch {
            val qr = attempt("Ошибка инициализации QR-кода") { authRepository.requestQr() } ?: return@launch
            _state.update { it.copy(qr = qr, isLoading = false) }
            poll()
        }
    }

    fun submitCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || _state.value.isLoading) return
        pollJob?.cancel()
        _state.update { it.copy(isLoading = true, codeError = null) }
        pollJob = viewModelScope.launch {
            val valid = attempt("Ошибка подтверждения кода") { authRepository.submitCode(trimmed) } ?: return@launch
            if (!valid) {
                _state.update { it.copy(isLoading = false, codeError = "Неверный код подтверждения") }
                return@launch
            }
            // Как в Meridius: после верного кода статус проверяется сразу, без ожидания.
            poll(immediate = true)
        }
    }

    fun openWebView() {
        pollJob?.cancel()
        _state.update { it.copy(step = LoginStep.WebView, error = null, reconnecting = false) }
    }

    fun closeWebView() = loadQr()

    /** Страница VK в WebView загрузилась: пробуем перенести её cookie и получить токен. */
    fun onWebViewPage(cookies: Map<String, String>) {
        if (webViewJob?.isActive == true || cookies.isEmpty()) return
        webViewJob = viewModelScope.launch {
            // Пока пользователь не вошёл, web_token отвечает ошибкой — это ожидаемо, просто ждём следующую страницу.
            runCatching { authRepository.completeWebViewLogin(cookies) }
                .onSuccess { _state.update { it.copy(step = LoginStep.Done) } }
                .onFailure { if (it is CancellationException) throw it }
        }
    }

    private suspend fun poll(immediate: Boolean = false) {
        if (!immediate) delay(VkWebConfig.QR_POLL_INTERVAL_MS)
        while (viewModelScope.isActive) {
            when (attempt("Ошибка проверки QR-кода") { authRepository.checkQr() } ?: return) {
                QrStatus.Confirmed -> {
                    _state.update { it.copy(step = LoginStep.Done, isLoading = false) }
                    return
                }
                QrStatus.NeedCode -> {
                    _state.update { it.copy(step = LoginStep.Code, isLoading = false) }
                    return
                }
                QrStatus.Expired -> {
                    loadQr()
                    return
                }
                QrStatus.Waiting -> Unit
            }
            delay(VkWebConfig.QR_POLL_INTERVAL_MS)
        }
    }

    /**
     * Сетевые сбои (нет DNS, обрыв, таймаут, 5xx) повторяются сами с нарастающей паузой: телефон
     * засыпает или меняет сеть, пока QR висит на экране. Ошибки VK показываются с кнопкой «Повторить».
     */
    private suspend fun <T> attempt(fallback: String, block: suspend () -> T): T? {
        var backoff = RETRY_MIN_MS
        while (true) {
            val result = runCatching { block() }
            val error = result.exceptionOrNull() ?: run {
                if (_state.value.reconnecting) _state.update { it.copy(reconnecting = false) }
                return result.getOrNull()
            }
            if (error is CancellationException) throw error
            if (error !is IOException) {
                _state.update { it.copy(isLoading = false, reconnecting = false, error = error.message ?: fallback) }
                return null
            }
            _state.update { it.copy(reconnecting = true) }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(RETRY_MAX_MS)
        }
    }

    private companion object {
        const val RETRY_MIN_MS = 2_000L
        const val RETRY_MAX_MS = 30_000L
    }
}
