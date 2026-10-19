package com.vkmusic.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.api.vk.AuthResult
import com.vkmusic.data.api.vk.CaptchaAnswer
import com.vkmusic.data.api.vk.OtpChannel
import com.vkmusic.data.api.vk.OtpResult
import com.vkmusic.data.api.vk.VkCredentials
import com.vkmusic.data.api.vk.otpChannel
import com.vkmusic.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginStep {
    data object Credentials : LoginStep

    /** type — `validation_type` из ответа VK: `2fa_email`, `2fa_sms`, `2fa_push`, `2fa_app`. */
    data class TwoFactor(val sid: String?, val type: String?, val phoneMask: String?) : LoginStep

    data class Captcha(val sid: String, val imageUrl: String) : LoginStep
    data object Done : LoginStep
}

data class LoginUiState(
    val step: LoginStep = LoginStep.Credentials,
    val isLoading: Boolean = false,
    val info: String? = null,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var credentials: VkCredentials? = null
    private var pendingCode: String? = null
    private var pendingSid: String? = null
    private var submitJob: Job? = null

    fun signIn(username: String, password: String) {
        credentials = VkCredentials(username.trim(), password)
        pendingCode = null
        pendingSid = null
        submit()
    }

    fun submitCode(code: String) {
        pendingCode = code.trim()
        submit()
    }

    fun requestSms() = submit(forceSms = true)

    fun requestCode(channel: OtpChannel) {
        val sid = pendingSid ?: return
        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, info = null) }
            when (val result = authRepository.sendOtp(sid, channel)) {
                OtpResult.Sent -> _state.update {
                    it.copy(isLoading = false, info = "Код отправлен: ${describe(channel)}")
                }
                is OtpResult.Rejected -> _state.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun submitCaptcha(sid: String, key: String) = submit(captcha = CaptchaAnswer(sid, key.trim()))

    fun backToCredentials() {
        submitJob?.cancel()
        credentials = null
        pendingCode = null
        pendingSid = null
        _state.value = LoginUiState()
    }

    fun continueInDemoMode() {
        viewModelScope.launch {
            authRepository.enterDemoMode()
            _state.update { it.copy(step = LoginStep.Done) }
        }
    }

    private fun describe(channel: OtpChannel): String = when (channel) {
        OtpChannel.Email -> "проверьте почту"
        OtpChannel.Sms -> "проверьте SMS"
        OtpChannel.Push -> "подтверждение в приложении VK"
    }

    private fun submit(captcha: CaptchaAnswer? = null, forceSms: Boolean = false) {
        val creds = credentials ?: return
        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, info = null) }
            val result = runCatching { authRepository.login(creds, pendingCode, captcha, pendingSid, forceSms) }
                .getOrElse {
                    if (it is CancellationException) throw it
                    AuthResult.Failure(it.message ?: "Нет соединения с VK")
                }
            if (result is AuthResult.NeedValidation) pendingSid = result.sid ?: pendingSid
            _state.update { current ->
                when (result) {
                    is AuthResult.Success -> {
                        credentials = null
                        pendingCode = null
                        pendingSid = null
                        LoginUiState(step = LoginStep.Done)
                    }
                    is AuthResult.NeedValidation -> {
                        val wrongCode = pendingCode != null && current.step is LoginStep.TwoFactor
                        pendingCode = null
                        val rejected = result.otp as? OtpResult.Rejected
                        current.copy(
                            isLoading = false,
                            step = LoginStep.TwoFactor(pendingSid, result.type, result.phoneMask),
                            info = otpChannel(result.type)?.let { channel ->
                                if (result.otp == OtpResult.Sent) "Код отправлен: ${describe(channel)}" else null
                            },
                            error = when {
                                wrongCode -> "Неверный код"
                                rejected != null -> rejected.message
                                else -> null
                            },
                        )
                    }
                    is AuthResult.NeedCaptcha -> current.copy(isLoading = false, step = LoginStep.Captcha(result.sid, result.imageUrl))
                    is AuthResult.Failure -> current.copy(isLoading = false, error = result.message)
                }
            }
        }
    }
}
