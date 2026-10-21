package com.vkmusic.player.audio

import com.vkmusic.data.repository.EqualizerSettings
import com.vkmusic.data.repository.NormalizerSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Текущие параметры обработки звука. Пишет [com.vkmusic.player.service.PlaybackService] из настроек,
 * читают аудиопроцессоры на потоке воспроизведения — поэтому значения неизменяемые и @Volatile.
 */
@Singleton
class AudioEffects @Inject constructor() {
    @Volatile
    var equalizer: EqualizerSettings = EqualizerSettings()

    @Volatile
    var normalizer: NormalizerSettings = NormalizerSettings()
}
