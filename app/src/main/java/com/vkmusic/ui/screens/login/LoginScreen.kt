package com.vkmusic.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vkmusic.R
import com.vkmusic.data.api.vk.OtpChannel
import com.vkmusic.ui.components.AppVersionText

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.step) {
        if (state.step == LoginStep.Done) onSignedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        when (val step = state.step) {
            LoginStep.Credentials, LoginStep.Done -> CredentialsForm(state.isLoading, viewModel::signIn)
            is LoginStep.TwoFactor -> TwoFactorForm(step, state.isLoading, viewModel)
            is LoginStep.Captcha -> CaptchaForm(step, state.isLoading, viewModel)
        }
        state.info?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.isLoading) CircularProgressIndicator()
        if (state.step == LoginStep.Credentials) {
            OutlinedButton(onClick = viewModel::continueInDemoMode, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                Text("Демо-режим без аккаунта")
            }
        }
        AppVersionText()
    }
}

@Composable
private fun CredentialsForm(isLoading: Boolean, onSubmit: (String, String) -> Unit) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    OutlinedTextField(
        value = login,
        onValueChange = { login = it },
        label = { Text("Телефон или email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Пароль") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onSubmit(login, password) },
        enabled = !isLoading && login.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Войти через ВКонтакте")
    }
}

@Composable
private fun TwoFactorForm(step: LoginStep.TwoFactor, isLoading: Boolean, viewModel: LoginViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    Text(
        when (step.type) {
            "2fa_email" -> "Мы отправили код подтверждения на вашу почту"
            "2fa_push" -> "Подтвердите вход в приложении VK или введите код из push-уведомления"
            "2fa_sms" -> "Введите код из SMS${step.phoneMask?.let { " на номер $it" }.orEmpty()}"
            else -> "Введите код из приложения для подтверждения входа или из личных сообщений VK"
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit) },
        label = { Text("Код подтверждения") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { viewModel.submitCode(code) }, enabled = !isLoading && code.length >= 4, modifier = Modifier.fillMaxWidth()) {
        Text("Подтвердить")
    }
    if (step.sid != null && step.type != "2fa_email") {
        TextButton(onClick = { viewModel.requestCode(OtpChannel.Email) }, enabled = !isLoading) {
            Text("Отправить код на почту")
        }
    }
    if (step.type != "2fa_sms") {
        TextButton(onClick = viewModel::requestSms, enabled = !isLoading) { Text("Отправить код по SMS") }
    }
    TextButton(onClick = viewModel::backToCredentials, enabled = !isLoading) { Text("Назад") }
}

@Composable
private fun CaptchaForm(step: LoginStep.Captcha, isLoading: Boolean, viewModel: LoginViewModel) {
    var key by rememberSaveable(step.sid) { mutableStateOf("") }
    Text("Введите символы с картинки", style = MaterialTheme.typography.bodyMedium)
    AsyncImage(model = step.imageUrl, contentDescription = "Капча", modifier = Modifier.height(64.dp))
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text("Символы") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { viewModel.submitCaptcha(step.sid, key) }, enabled = !isLoading && key.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Продолжить")
    }
    TextButton(onClick = viewModel::backToCredentials, enabled = !isLoading) { Text("Назад") }
}
