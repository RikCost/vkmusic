package com.vkmusic.ui.screens.login

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.vkmusic.R
import com.vkmusic.data.web.VkWebConfig
import com.vkmusic.ui.components.AppVersionText
import com.vkmusic.ui.components.BackTopBar

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.step) {
        if (state.step == LoginStep.Done) onSignedIn()
    }

    when (state.step) {
        LoginStep.WebView -> WebViewLogin(onCookies = viewModel::onWebViewPage, onBack = viewModel::closeWebView)
        else -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("Музыкальный плеер для VK", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.step == LoginStep.Code) CodeStep(state, viewModel) else QrStep(state, viewModel)
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                OutlinedButton(onClick = viewModel::loadQr) { Text("Повторить") }
            }
            Box(Modifier.height(8.dp))
            TextButton(onClick = viewModel::continueInDemoMode) { Text("Демо-режим без аккаунта") }
            AppVersionText()
        }
    }
}

@Composable
private fun QrStep(state: LoginUiState, viewModel: LoginViewModel) {
    val context = LocalContext.current
    val qr = state.qr
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(top = 12.dp)
            .size(248.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White),
    ) {
        if (qr == null) {
            if (state.error == null) CircularProgressIndicator(color = Color.Black)
        } else {
            QrImage(qr.url, Modifier.size(224.dp))
        }
    }
    Text(
        if (qr == null && state.error == null) "Загрузка QR-кода..." else "Отсканируйте QR-код, используя камеру вашего телефона",
        textAlign = TextAlign.Center,
    )
    if (qr != null) {
        // На том же телефоне QR не отсканировать — ссылку из него можно открыть в приложении VK.
        OutlinedButton(onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, qr.url.toUri()))
            } catch (_: ActivityNotFoundException) {
                viewModel.openWebView()
            }
        }) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, Modifier.padding(end = 8.dp))
            Text("Подтвердить в приложении VK")
        }
    }
    Button(onClick = viewModel::openWebView) {
        Icon(Icons.Rounded.Language, contentDescription = null, Modifier.padding(end = 8.dp))
        Text("Войти через браузер")
    }
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, VkWebConfig.BASE_URL.toUri())) }) {
        Text("Создать аккаунт VK")
    }
}

@Composable
private fun CodeStep(state: LoginUiState, viewModel: LoginViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    Text("Подтверждение входа", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
    Text("Введите код из приложения VK", color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        label = { Text("Код") },
        singleLine = true,
        isError = state.codeError != null,
        supportingText = state.codeError?.let { error -> @Composable { Text(error) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { viewModel.submitCode(code) }, enabled = code.isNotBlank() && !state.isLoading, modifier = Modifier.fillMaxWidth()) {
        if (state.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Подтвердить")
    }
    TextButton(onClick = viewModel::loadQr) { Text("Новый QR-код") }
}

@Composable
private fun QrImage(content: String, modifier: Modifier) {
    val bitmap = remember(content) {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(bitmap, contentDescription = "QR-код для входа", filterQuality = FilterQuality.None, modifier = modifier)
}

/**
 * Дополнительный вход: обычная страница VK во встроенном браузере. Когда VK пускает на vk.ru,
 * cookie страницы передаются в [onCookies] — дальше та же веб-сессия, что и после QR.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewLogin(onCookies: (Map<String, String>) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        BackTopBar(title = "Вход через браузер", subtitle = "vk.ru", onBack = onBack)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Тот же десктопный UA, что у Meridius: VK отдаёт полную веб-версию, а не m.vk.ru.
                    settings.userAgentString = VkWebConfig.USER_AGENT
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            val host = url.toUri().host ?: return
                            if (host != "vk.ru" && host != "m.vk.ru") return
                            cookieManager.flush()
                            val cookies = VkWebConfig.SESSION_URLS
                                .associateWith { cookieManager.getCookie(it).orEmpty() }
                                .filterValues { it.isNotBlank() }
                            onCookies(cookies)
                        }
                    }
                    loadUrl(VkWebConfig.BASE_URL)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
