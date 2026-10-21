package com.vkmusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vkmusic.data.model.Track
import com.vkmusic.ui.formatTotalDuration
import java.util.Locale
import kotlin.math.roundToInt

enum class BulkKind(val verb: String, val title: String, val doneLabel: String, val allDone: String) {
    Download("Скачать", "Скачать", "Уже скачано", "Все треки уже скачаны"),
    Export("Сохранить", "Сохранить в MP3", "Уже сохранено", "Все треки уже сохранены в MP3"),
}

private class BulkRequest(val kind: BulkKind, val tracks: List<Track>, val skipped: Int, val onConfirm: (List<Track>) -> Unit)

/**
 * Проверка перед массовой загрузкой (плейлист, альбом, вся музыка): больше [BULK_THRESHOLD] треков —
 * диалог с выбором, сколько из них скачать. Меньше — действие сразу.
 */
@Stable
class BulkDownloadGuard {
    private var request by mutableStateOf<BulkRequest?>(null)

    /** [skipped] — сколько треков списка уже скачано/сохранено и в [tracks] не попало (для текста диалога). */
    fun run(kind: BulkKind, tracks: List<Track>, skipped: Int = 0, action: (List<Track>) -> Unit) {
        if (tracks.size > BULK_THRESHOLD) request = BulkRequest(kind, tracks, skipped, action) else action(tracks)
    }

    /** Скачать оставшиеся треки списка: уже скачанные не считаются и не предлагаются заново. */
    fun download(actions: TrackActionsViewModel, tracks: List<Track>) {
        val pending = actions.pendingDownloads(tracks)
        if (pending.isEmpty()) actions.notify(BulkKind.Download.allDone) else run(BulkKind.Download, pending, tracks.size - pending.size, actions::download)
    }

    fun export(actions: TrackActionsViewModel, tracks: List<Track>, folder: String? = null) {
        val pending = actions.pendingExports(tracks)
        if (pending.isEmpty()) {
            actions.notify(BulkKind.Export.allDone)
        } else {
            run(BulkKind.Export, pending, tracks.size - pending.size) { actions.exportMp3(it, folder) }
        }
    }

    @Composable
    fun Dialog() {
        val current = request ?: return
        BulkDownloadDialog(
            kind = current.kind,
            tracks = current.tracks,
            skipped = current.skipped,
            onConfirm = { count ->
                request = null
                current.onConfirm(current.tracks.take(count))
            },
            onDismiss = { request = null },
        )
    }
}

/** Сторож массовой загрузки; диалог появляется там, где вызван [BulkDownloadGuard.Dialog]. */
@Composable
fun rememberBulkDownloadGuard(): BulkDownloadGuard = remember { BulkDownloadGuard() }.also { it.Dialog() }

@Composable
private fun BulkDownloadDialog(kind: BulkKind, tracks: List<Track>, skipped: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val total = tracks.size
    var selected by remember(tracks) { mutableFloatStateOf(total.toFloat()) }
    val count = selected.roundToInt().coerceIn(1, total)
    val seconds = tracks.take(count).sumOf { it.durationSec.toLong() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${kind.title}: $total треков?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Это много: загрузка займёт время, трафик и место на устройстве. " +
                        "Можно взять только часть — треки берутся по порядку списка.",
                )
                if (skipped > 0) {
                    Text(
                        "${kind.doneLabel}: $skipped — они пропускаются.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("Треков: $count из $total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                }
                Slider(
                    value = selected,
                    onValueChange = { selected = it },
                    valueRange = 1f..total.toFloat(),
                )
                Text(
                    "${formatTotalDuration(seconds)} • около ${formatSize(seconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(count) }) {
                Text(if (count == total) "${kind.verb} все" else "${kind.verb} $count")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// Оценка по 320 кбит/с — верхняя граница качества VK; реальные файлы обычно не больше.
private fun formatSize(seconds: Long): String {
    val megabytes = seconds * BITRATE_KBPS / 8.0 / 1024.0
    return if (megabytes >= 1024) String.format(Locale.ROOT, "%.1f ГБ", megabytes / 1024) else "${megabytes.roundToInt()} МБ"
}

const val BULK_THRESHOLD = 20
private const val BITRATE_KBPS = 320
