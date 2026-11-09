@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.player.service

import androidx.media3.common.C
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import com.vkmusic.player.queue.httpResponseCode
import com.vkmusic.player.queue.isConnectionProblem
import kotlin.math.min

/**
 * Повторы загрузки трека. По умолчанию Media3 сдаётся после трёх неудач подряд, и в лифте или метро
 * плеер вставал с ошибкой. Здесь без сети загрузка повторяется сколько угодно, и, когда сеть вернётся, трек
 * дозагружается с того же места. Пока в буфере что-то есть, музыка вообще не прерывается.
 *
 * Отказ CDN (4xx: ссылка истекла, трек закрыт правообладателем) повторами не лечится: такая ошибка
 * уходит в плеер сразу, а он обновит ссылку или пропустит трек.
 */
class PatientLoadErrorPolicy(private val isOnline: () -> Boolean) : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long =
        patientRetryDelay(loadErrorInfo.exception, loadErrorInfo.errorCount, isOnline()) ?: super.getRetryDelayMsFor(loadErrorInfo)

    // Ошибка всплывает в плеер только тогда, когда повторы закончились (см. выше), а не на третьей неудаче.
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = Int.MAX_VALUE
}

/**
 * Пауза перед повтором загрузки ([attempt] — какая по счёту неудача подряд): [C.TIME_UNSET] — сдаться,
 * null — решает политика Media3 по умолчанию.
 */
internal fun patientRetryDelay(error: Throwable, attempt: Int, online: Boolean): Long? = when {
    // С сетью, но без связи с VK, ждать вечно незачем: после нескольких попыток решает плеер.
    error.isConnectionProblem() ->
        if (!online || attempt <= ONLINE_RETRIES) min(attempt * RETRY_STEP_MS, MAX_RETRY_DELAY_MS) else C.TIME_UNSET
    error.httpResponseCode()?.let { it in CLIENT_ERRORS } == true -> C.TIME_UNSET
    attempt > DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT -> C.TIME_UNSET
    else -> null
}

private const val ONLINE_RETRIES = 6
private const val RETRY_STEP_MS = 1_000L
private const val MAX_RETRY_DELAY_MS = 5_000L
private val CLIENT_ERRORS = 400..499
