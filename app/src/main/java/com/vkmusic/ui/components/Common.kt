package com.vkmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.PrimaryScrollableTabRow
import com.vkmusic.ui.formatTracks
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageTopBar(title: String, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun MessageBox(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 12.dp).size(48.dp),
            )
        }
        Text(
            text = text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Круглая акцентная кнопка воспроизведения — главный элемент шапок Meridius. */
@Composable
fun RoundPlayButton(onClick: () -> Unit, enabled: Boolean = true, icon: ImageVector = Icons.Rounded.PlayArrow, size: Int = 48) {
    FilledIconButton(onClick = onClick, enabled = enabled, shape = CircleShape, modifier = Modifier.size(size.dp)) {
        Icon(icon, contentDescription = "Слушать", modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
fun PlayAllRow(tracks: List<Track>, actions: TrackActionsViewModel, modifier: Modifier = Modifier, downloadsMode: Boolean = false) {
    val bulk = rememberBulkDownloadGuard()
    val downloads by actions.downloads.collectAsStateWithLifecycle()
    val hasActive = downloads.values.any { it.status == DownloadStatus.Queued || it.status is DownloadStatus.Downloading }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundPlayButton(onClick = { actions.play(tracks, 0) }, enabled = tracks.isNotEmpty())
        IconButton(onClick = { actions.shuffle(tracks) }, enabled = tracks.isNotEmpty()) {
            Icon(Icons.Rounded.Shuffle, contentDescription = "Перемешать")
        }
        // На вкладке «Загрузки» всё уже скачано или качается — вместо «Скачать» там отмена очереди.
        if (downloadsMode) {
            IconButton(onClick = actions::cancelDownloads, enabled = hasActive) {
                Icon(Icons.Rounded.Cancel, contentDescription = "Отменить загрузки")
            }
        } else {
            IconButton(onClick = { bulk.download(actions, tracks) }, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Rounded.Download, contentDescription = "Скачать все")
            }
        }
        IconButton(
            onClick = { bulk.export(actions, tracks) },
            enabled = tracks.isNotEmpty(),
        ) {
            Icon(Icons.Rounded.SaveAlt, contentDescription = "Сохранить все в MP3")
        }
        Text(
            formatTracks(tracks.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Поле «Поиск музыки» из шапки списка Meridius: фильтрует уже загруженные треки. */
@Composable
fun FilterField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Поиск музыки") {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Clear, contentDescription = "Очистить") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

fun List<Track>.filteredBy(query: String): List<Track> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }
}

@Composable
fun AccentTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    // Прокручиваемая строка: вкладка шириной по своему названию и не переносит его на две строки.
    PrimaryScrollableTabRow(
        selectedTabIndex = selected,
        containerColor = MaterialTheme.colorScheme.background,
        edgePadding = 8.dp,
    ) {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = index == selected,
                onClick = { onSelect(index) },
                text = { Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSourcePage(
    state: LoadState<List<Track>>,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    emptyText: String = "Здесь пока пусто",
    emptyIcon: ImageVector = Icons.Rounded.LibraryMusic,
    showPlayAll: Boolean = true,
    downloadsMode: Boolean = false,
    filter: String = "",
    header: LazyListScope.() -> Unit = {},
    actions: TrackActionsViewModel = hiltViewModel(),
) {
    val tracks = state.data.filteredBy(filter)
    PullToRefreshBox(
        isRefreshing = state.isLoading && state.data.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        TrackList(
            tracks = tracks,
            contentPadding = contentPadding,
            actions = actions,
            header = {
                header()
                when {
                    state.error != null -> item { MessageBox(state.error, icon = Icons.Rounded.CloudOff) }
                    state.isLoading && state.data.isEmpty() -> item { ListSkeleton() }
                    state.data.isEmpty() -> item { MessageBox(emptyText, icon = emptyIcon) }
                    tracks.isEmpty() -> item { MessageBox("Ничего не найдено", icon = Icons.Rounded.SearchOff) }
                    showPlayAll -> item { PlayAllRow(tracks, actions, downloadsMode = downloadsMode) }
                }
            },
        )
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, onOpen: (Playlist) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(140.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpen(playlist) }.padding(4.dp)) {
        Artwork(playlist.coverUrl, playlist.id, Modifier.fillMaxWidth().aspectRatio(1f), cornerRadius = 6.dp)
        Text(
            playlist.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            playlist.subtitle.ifBlank { formatTracks(playlist.trackCount) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PlaylistsRow(playlists: List<Playlist>, onOpen: (Playlist) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(playlists, key = { it.id }) { PlaylistCard(it, onOpen) }
    }
}

@Composable
fun CreatePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый плейлист") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title.trim()) }, enabled = title.isNotBlank()) { Text("Создать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
fun EditPlaylistDialog(playlist: Playlist, onSave: (title: String, description: String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(playlist.title) }
    var description by rememberSaveable { mutableStateOf(playlist.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать плейлист") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Описание") }, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim(), description.trim()) }, enabled = title.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun SecondaryIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(contentColor = tint)) {
        Icon(icon, contentDescription = description)
    }
}
