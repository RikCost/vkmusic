package com.vkmusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vkmusic.data.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, subtitle: String? = null) {
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
    )
}

@Composable
fun MessageBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
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
fun PlayAllRow(tracks: List<Track>, actions: TrackActionsViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { actions.play(tracks, 0) }, enabled = tracks.isNotEmpty()) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, Modifier.padding(end = 4.dp).size(ButtonDefaults.IconSize))
            Text("Слушать")
        }
        FilledTonalButton(onClick = { actions.shuffle(tracks) }, enabled = tracks.isNotEmpty()) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, Modifier.padding(end = 4.dp).size(ButtonDefaults.IconSize))
            Text("Перемешать")
        }
        FilledTonalIconButton(onClick = { actions.download(tracks) }, enabled = tracks.isNotEmpty()) {
            Icon(Icons.Rounded.Download, contentDescription = "Скачать все")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSourcePage(
    state: LoadState<List<Track>>,
    onRefresh: () -> Unit,
    onSimilar: (Track) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    emptyText: String = "Здесь пока пусто",
    showPlayAll: Boolean = true,
    header: LazyListScope.() -> Unit = {},
    actions: TrackActionsViewModel = hiltViewModel(),
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading && state.data.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        TrackList(
            tracks = state.data,
            onSimilar = onSimilar,
            contentPadding = contentPadding,
            actions = actions,
            header = {
                header()
                when {
                    state.error != null -> item { MessageBox(state.error) }
                    state.isLoading && state.data.isEmpty() -> item { LoadingBox() }
                    state.data.isEmpty() -> item { MessageBox(emptyText) }
                    showPlayAll -> item { PlayAllRow(state.data, actions) }
                }
            },
        )
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
