package com.vkmusic.ui.screens.settings

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.repository.AppSettings
import com.vkmusic.data.repository.NormalizerSettings
import com.vkmusic.data.repository.SettingsRepository
import com.vkmusic.data.export.ExportStorage
import com.vkmusic.data.repository.DownloadRepository
import com.vkmusic.data.web.VkWebDiagnostics
import com.vkmusic.player.audio.EqualizerPresets
import com.vkmusic.ui.components.AppVersionText
import com.vkmusic.ui.components.BackTopBar
import com.vkmusic.ui.components.ConfirmDialog
import com.vkmusic.ui.components.SectionTitle
import com.vkmusic.ui.navigation.LocalNavigator
import com.vkmusic.ui.screens.bottomOnly
import com.vkmusic.ui.theme.MeridiusTheme
import com.vkmusic.ui.theme.MeridiusThemes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val diagnostics: VkWebDiagnostics,
    private val exportStorage: ExportStorage,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    /** Сколько офлайн-загрузок и сколько места они занимают (включая недокачанные). */
    val downloadsSummary: StateFlow<Pair<Int, Long>> = downloadRepository.downloads
        .map { downloads -> downloads.size to downloads.values.sumOf { it.bytes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0L)

    fun removeAllDownloads() = downloadRepository.removeAll()

    fun exportFolderName(treeUri: String?): String = exportStorage.displayName(treeUri)

    fun setExportFolder(treeUri: String?) = launch { repository.setExportTreeUri(treeUri) }

    private val _report = MutableStateFlow<String?>(null)
    val report: StateFlow<String?> = _report.asStateFlow()

    fun runDiagnostics() {
        _report.value = "Проверка…"
        viewModelScope.launch { _report.value = runCatching { diagnostics.run() }.getOrElse { "Ошибка: ${it.message}" } }
    }

    fun runSearchDiagnostics(query: String) {
        if (query.isBlank()) return
        _report.value = "Проверка поиска…"
        viewModelScope.launch { _report.value = runCatching { diagnostics.searchReport(query.trim()) }.getOrElse { "Ошибка: ${it.message}" } }
    }

    val settings: StateFlow<AppSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTheme(id: String) = launch { repository.setTheme(id) }
    fun setRewind(value: Boolean) = launch { repository.setRewindToStart(value) }
    fun setSaveLatest(value: Boolean) = launch { repository.setSaveLatest(value) }
    fun setPlayLatest(value: Boolean) = launch { repository.setPlayLatestOnStart(value) }
    fun setCrossfade(seconds: Int) = launch { repository.setCrossfadeSeconds(seconds) }
    fun setNormalizer(value: NormalizerSettings) = launch { repository.setNormalizer(value) }
    fun setEqualizerEnabled(value: Boolean) = launch { repository.setEqualizerEnabled(value) }

    fun selectPreset(name: String) = launch {
        val levels = EqualizerPresets.presets[name] ?: EqualizerPresets.flat
        repository.setEqualizerPreset(name, levels)
    }

    // Ручная правка полосы переводит эквалайзер в «Custom», как в Meridius.
    fun setBand(index: Int, value: Float) = launch {
        val current = settings.value?.equalizer?.levels ?: EqualizerPresets.flat
        repository.setEqualizerPreset(EqualizerPresets.CUSTOM, current.toMutableList().also { it[index] = value })
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val navigator = LocalNavigator.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = "Настройки", onBack = navigator::back)
        if (current == null) return@Column
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.bottomOnly()) {
            item { SectionTitle("Внешний вид") }
            item { ThemePicker(current.themeId, viewModel::setTheme) }

            item { SectionTitle("Плеер") }
            item {
                SwitchRow(
                    title = "Перемотка в начало трека",
                    subtitle = "«Назад» начинает трек заново, если он играет 5 секунд и дольше",
                    checked = current.rewindToStart,
                    onChange = viewModel::setRewind,
                )
            }
            item { SwitchRow("Сохранять текущий трек", "Очередь и позиция восстанавливаются после перезапуска", current.saveLatest, viewModel::setSaveLatest) }
            item {
                SwitchRow(
                    title = "Воспроизводить при запуске",
                    subtitle = null,
                    checked = current.playLatestOnStart,
                    onChange = viewModel::setPlayLatest,
                    enabled = current.saveLatest,
                )
            }
            item {
                LabeledSlider(
                    title = "Плавный переход между треками",
                    valueText = if (current.crossfadeSeconds == 0) "Выкл." else "${current.crossfadeSeconds} с",
                    hint = "Конец трека затухает, пока нарастает следующий",
                    value = current.crossfadeSeconds.toFloat(),
                    range = 0f..MAX_CROSSFADE_SECONDS.toFloat(),
                    steps = MAX_CROSSFADE_SECONDS - 1,
                    enabled = true,
                    onChange = { viewModel.setCrossfade(it.roundToInt()) },
                )
            }

            item { SectionTitle("Нормализация громкости") }
            item { NormalizerSection(current.normalizer, viewModel::setNormalizer) }

            item { SectionTitle("Эквалайзер") }
            item {
                SwitchRow("Включить эквалайзер", "18 полос, пресеты Meridius", current.equalizer.enabled, viewModel::setEqualizerEnabled)
            }
            item { PresetPicker(current.equalizer.preset, viewModel::selectPreset) }
            itemsIndexed(EqualizerPresets.frequencies.toList()) { index, frequency ->
                BandSlider(
                    label = EqualizerPresets.label(frequency),
                    value = current.equalizer.levels.getOrElse(index) { 0f },
                    enabled = current.equalizer.enabled,
                    onChange = { viewModel.setBand(index, it) },
                )
            }

            item { SectionTitle("Загрузки") }
            item { DownloadsSection(viewModel) }

            item { SectionTitle("Сохранение в MP3") }
            item { ExportFolderSection(current.exportTreeUri, viewModel) }

            item { SectionTitle("Диагностика веб-VK") }
            item { DiagnosticsSection(viewModel) }

            item { SectionTitle("Аккаунт") }
            item {
                OutlinedButton(onClick = onSignOut, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, Modifier.padding(end = 8.dp))
                    Text("Выйти")
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    AppVersionText()
                    Text(
                        "Интерфейс и функции перенесены из десктопного плеера Meridius.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsSection(viewModel: SettingsViewModel) {
    val (count, bytes) = viewModel.downloadsSummary.collectAsStateWithLifecycle().value
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (count == 0) "Скачанных треков нет" else "Скачано треков: $count • ${formatBytes(bytes)}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Офлайн-копии для прослушивания без сети. Сохранённые в папку MP3-файлы здесь не учитываются и не удаляются.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { confirm = true }, enabled = count > 0) { Text("Удалить все загрузки") }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Удалить все загрузки?",
            text = "Будут удалены $count треков (${formatBytes(bytes)}). Без сети они перестанут играть; в VK всё останется.",
            confirm = "Удалить",
            onConfirm = viewModel::removeAllDownloads,
            onDismiss = { confirm = false },
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1024) String.format(Locale.ROOT, "%.1f ГБ", megabytes / 1024) else "${megabytes.roundToInt()} МБ"
}

@Composable
private fun ExportFolderSection(treeUri: String?, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Без постоянного разрешения доступ к папке пропадёт после перезапуска.
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        viewModel.setExportFolder(uri.toString())
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Папка: ${viewModel.exportFolderName(treeUri)}", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Сюда сохраняются треки из меню «Сохранить в MP3»; плейлисты — в подпапку со своим названием.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(treeUri?.toUri()) }) { Text("Выбрать папку") }
            if (treeUri != null) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            treeUri.toUri(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }
                    viewModel.setExportFolder(null)
                }) { Text("По умолчанию") }
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(viewModel: SettingsViewModel) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Проверяет, отвечает ли веб-версия VK (через неё идут ваша музыка, плейлисты и поиск треков). " +
                "В отчёте нет паролей, значений cookie и токенов, но есть id аккаунта и начало ответа VK.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var query by remember { mutableStateOf("") }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Запрос для проверки поиска") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::runDiagnostics) { Text("Проверить") }
            OutlinedButton(onClick = { viewModel.runSearchDiagnostics(query) }, enabled = query.isNotBlank()) { Text("Поиск") }
            report?.let { text -> OutlinedButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Диагностика веб-VK", text))
                }) { Text("Скопировать") } }
        }
        report?.let { text ->
            SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun ThemePicker(selectedId: String, onSelect: (String) -> Unit) {
    val themes = buildList {
        addAll(MeridiusThemes.all.map { it.id to it })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(MeridiusThemes.SYSTEM to null)
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(themes, key = { it.first }) { (id, theme) -> ThemeCard(theme, id == selectedId) { onSelect(id) } }
    }
}

@Composable
private fun ThemeCard(theme: MeridiusTheme?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background = theme?.background ?: colors.surfaceContainerHighest
    val accent = theme?.accent ?: colors.primary
    Column(
        Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(BorderStroke(2.dp, if (selected) colors.primary else colors.outlineVariant), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().background(background).padding(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(24.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                val line = if ((theme?.isDark) != false) Color.White else Color.Black
                Box(Modifier.size(width = 72.dp, height = 6.dp).background(line.copy(alpha = 0.7f), RoundedCornerShape(3.dp)))
                Box(Modifier.size(width = 48.dp, height = 6.dp).background(line.copy(alpha = 0.35f), RoundedCornerShape(3.dp)))
            }
        }
        Text(
            theme?.name ?: "Material You",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

private const val MAX_CROSSFADE_SECONDS = 12

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { text -> @Composable { Text(text) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
    )
}

@Composable
private fun NormalizerSection(value: NormalizerSettings, onChange: (NormalizerSettings) -> Unit) {
    Column {
        SwitchRow(
            title = "Нормализация громкости трека",
            subtitle = "Усредняет громкость треков. Не включайте, если слушаете на средне-высокой громкости",
            checked = value.enabled,
            onChange = { onChange(value.copy(enabled = it)) },
        )
        LabeledSlider(
            title = "Целевая громкость",
            valueText = "${value.targetLufs.roundToInt()} LUFS",
            hint = "−14 LUFS — стандарт стриминговых сервисов",
            value = value.targetLufs,
            range = -24f..-8f,
            steps = 15,
            enabled = value.enabled,
            onChange = { onChange(value.copy(targetLufs = it.roundToInt().toFloat())) },
        )
        LabeledSlider(
            title = "Максимальная корректировка",
            valueText = "±${value.maxGainDb.roundToInt()} дБ",
            hint = null,
            value = value.maxGainDb,
            range = 1f..20f,
            steps = 18,
            enabled = value.enabled,
            onChange = { onChange(value.copy(maxGainDb = it.roundToInt().toFloat())) },
        )
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    valueText: String,
    hint: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, enabled = enabled)
    }
}

@Composable
private fun PresetPicker(selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 16.dp)) {
        OutlinedButton(onClick = { open = true }) {
            Text("Пресет: $selected")
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf(EqualizerPresets.CUSTOM) + EqualizerPresets.presets.keys).forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = { if (name == selected) Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        onSelect(name)
                    },
                )
            }
        }
    }
}

@Composable
private fun BandSlider(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("$label Гц", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onChange((it * 10).roundToInt() / 10f) }
                dragging = null
            },
            valueRange = EqualizerPresets.MIN_DB..EqualizerPresets.MAX_DB,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            String.format(Locale.ROOT, "%+.1f", shown),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp).padding(start = 6.dp),
        )
    }
}
