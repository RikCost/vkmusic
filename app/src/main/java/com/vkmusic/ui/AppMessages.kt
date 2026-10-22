package com.vkmusic.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сообщения, которые должны пережить закрытие экрана (например, «Плейлист удалён» после возврата назад):
 * их показывает корневой экран, а не тот, что их отправил.
 */
@Singleton
class AppMessages @Inject constructor() {
    private val channel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = channel.receiveAsFlow()

    fun show(message: String) {
        channel.trySend(message)
    }
}
