package com.vkmusic.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vkmusic.player.audio.EqualizerPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerSettings(
    val enabled: Boolean = false,
    val preset: String = EqualizerPresets.CUSTOM,
    val levels: List<Float> = EqualizerPresets.flat,
)

// Значения по умолчанию — как у Meridius: -14 LUFS (стандарт стримингов) и не больше ±10 дБ коррекции.
data class NormalizerSettings(
    val enabled: Boolean = false,
    val targetLufs: Float = -14f,
    val maxGainDb: Float = 10f,
)

data class AppSettings(
    val isDemoMode: Boolean = false,
    val themeId: String = DEFAULT_THEME,
    val equalizer: EqualizerSettings = EqualizerSettings(),
    val normalizer: NormalizerSettings = NormalizerSettings(),
    val rewindToStart: Boolean = true,
    val saveLatest: Boolean = true,
    val playLatestOnStart: Boolean = false,
    val playbackSpeed: Float = 1f,
    val mixOptions: Map<String, List<String>> = emptyMap(),
    // Папка для MP3 — URI дерева SAF; null — «Музыка/MuVK» по умолчанию.
    val exportTreeUri: String? = null,
) {
    companion object {
        const val DEFAULT_THEME = "classic"
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    private val mixSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            isDemoMode = prefs[Keys.DEMO_MODE] ?: false,
            themeId = prefs[Keys.THEME] ?: AppSettings.DEFAULT_THEME,
            equalizer = EqualizerSettings(
                enabled = prefs[Keys.EQ_ENABLED] ?: false,
                preset = prefs[Keys.EQ_PRESET] ?: EqualizerPresets.CUSTOM,
                levels = prefs[Keys.EQ_LEVELS]?.let(::parseLevels) ?: EqualizerPresets.flat,
            ),
            normalizer = NormalizerSettings(
                enabled = prefs[Keys.NORMALIZER_ENABLED] ?: false,
                targetLufs = prefs[Keys.NORMALIZER_TARGET] ?: -14f,
                maxGainDb = prefs[Keys.NORMALIZER_MAX] ?: 10f,
            ),
            rewindToStart = prefs[Keys.REWIND] ?: true,
            saveLatest = prefs[Keys.SAVE_LATEST] ?: true,
            playLatestOnStart = prefs[Keys.PLAY_LATEST] ?: false,
            playbackSpeed = prefs[Keys.SPEED] ?: 1f,
            mixOptions = prefs[Keys.MIX_OPTIONS]?.let { runCatching { json.decodeFromString(mixSerializer, it) }.getOrNull() }
                .orEmpty(),
            exportTreeUri = prefs[Keys.EXPORT_TREE],
        )
    }.distinctUntilChanged()

    suspend fun setDemoMode(value: Boolean) = dataStore.edit { it[Keys.DEMO_MODE] = value }

    suspend fun setTheme(id: String) = dataStore.edit { it[Keys.THEME] = id }

    suspend fun setEqualizerEnabled(value: Boolean) = dataStore.edit { it[Keys.EQ_ENABLED] = value }

    suspend fun setEqualizerPreset(name: String, levels: List<Float>) = dataStore.edit {
        it[Keys.EQ_PRESET] = name
        it[Keys.EQ_LEVELS] = levels.joinToString(",")
    }

    suspend fun setNormalizer(value: NormalizerSettings) = dataStore.edit {
        it[Keys.NORMALIZER_ENABLED] = value.enabled
        it[Keys.NORMALIZER_TARGET] = value.targetLufs
        it[Keys.NORMALIZER_MAX] = value.maxGainDb
    }

    suspend fun setRewindToStart(value: Boolean) = dataStore.edit { it[Keys.REWIND] = value }

    suspend fun setSaveLatest(value: Boolean) = dataStore.edit { it[Keys.SAVE_LATEST] = value }

    suspend fun setPlayLatestOnStart(value: Boolean) = dataStore.edit { it[Keys.PLAY_LATEST] = value }

    suspend fun setPlaybackSpeed(value: Float) = dataStore.edit { it[Keys.SPEED] = value }

    suspend fun setMixOptions(value: Map<String, List<String>>) = dataStore.edit {
        it[Keys.MIX_OPTIONS] = json.encodeToString(mixSerializer, value)
    }

    suspend fun setExportTreeUri(value: String?) = dataStore.edit {
        if (value == null) it.remove(Keys.EXPORT_TREE) else it[Keys.EXPORT_TREE] = value
    }

    private fun parseLevels(raw: String): List<Float>? =
        raw.split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == EqualizerPresets.frequencies.size }

    private object Keys {
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val THEME = stringPreferencesKey("theme")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_LEVELS = stringPreferencesKey("eq_levels")
        val NORMALIZER_ENABLED = booleanPreferencesKey("normalizer_enabled")
        val NORMALIZER_TARGET = floatPreferencesKey("normalizer_target")
        val NORMALIZER_MAX = floatPreferencesKey("normalizer_max")
        val REWIND = booleanPreferencesKey("rewind_to_start")
        val SAVE_LATEST = booleanPreferencesKey("save_latest")
        val PLAY_LATEST = booleanPreferencesKey("play_latest_on_start")
        val SPEED = floatPreferencesKey("playback_speed")
        val MIX_OPTIONS = stringPreferencesKey("mix_options")
        val EXPORT_TREE = stringPreferencesKey("export_tree_uri")
    }
}
