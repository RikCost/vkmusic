package com.vkmusic.ui.screens.home

import com.vkmusic.R
import androidx.compose.ui.res.stringResource
import com.vkmusic.ui.components.currentIsPlaying
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import com.vkmusic.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vkmusic.data.model.MixCategory
import com.vkmusic.data.model.Profile
import com.vkmusic.ui.LocalSnackbarHostState
import com.vkmusic.ui.components.Artwork
import com.vkmusic.ui.components.LoadingBox
import com.vkmusic.ui.components.MessageBox
import com.vkmusic.ui.components.PlaylistsRow
import com.vkmusic.ui.components.RoundPlayButton
import com.vkmusic.ui.components.SectionTitle
import com.vkmusic.ui.components.TrackActionsMessages
import com.vkmusic.ui.components.TrackActionsViewModel
import com.vkmusic.ui.components.currentTrackId
import com.vkmusic.ui.components.trackItems
import com.vkmusic.ui.navigation.LocalNavigator
import com.vkmusic.ui.screens.bottomOnly

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    viewModel: HomeViewModel = hiltViewModel(),
    actions: TrackActionsViewModel = hiltViewModel(),
) {
    val navigator = LocalNavigator.current
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val mix by viewModel.mix.collectAsStateWithLifecycle()
    val mixSelected by viewModel.mixSelected.collectAsStateWithLifecycle()
    val currentId = currentTrackId(actions)
    val isPlaying = currentIsPlaying(actions)
    var mixSettingsOpen by rememberSaveable { mutableStateOf(false) }

    TrackActionsMessages(actions)
    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    PullToRefreshBox(
        isRefreshing = recommendations.isLoading && recommendations.data.isNotEmpty(),
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.bottomOnly()) {
            item { ProfileHeader(profile, onSettings = navigator::openSettings) }
            item {
                VkMixCard(
                    selectedCount = mixSelected.values.sumOf { it.size },
                    isStarting = mix.isStarting,
                    onPlay = viewModel::playMix,
                    onConfigure = {
                        viewModel.loadMixSettings()
                        mixSettingsOpen = true
                    },
                )
            }
            if (playlists.isNotEmpty()) {
                item { SectionTitle("Ваши плейлисты") }
                item { PlaylistsRow(playlists, navigator::openPlaylist) }
            }
            item {
                SectionTitle("Рекомендации") {
                    if (recommendations.data.isNotEmpty()) {
                        RoundPlayButton(onClick = { actions.play(recommendations.data, 0) }, size = 40)
                    }
                }
            }
            when {
                recommendations.error != null -> item { MessageBox(recommendations.error.orEmpty()) }
                recommendations.isLoading && recommendations.data.isEmpty() -> item { LoadingBox() }
            }
            trackItems(recommendations.data, currentId, actions, keyPrefix = "rec_", isPlaying = isPlaying)
        }
    }

    if (mixSettingsOpen) {
        MixSettingsSheet(
            categories = mix.categories.data,
            isLoading = mix.categories.isLoading,
            error = mix.categories.error,
            selected = mixSelected,
            onToggle = viewModel::toggleMixOption,
            onReset = viewModel::resetMix,
            onPlay = {
                mixSettingsOpen = false
                viewModel.playMix()
            },
            onDismiss = { mixSettingsOpen = false },
        )
    }
}

/** Блок профиля из левой колонки Meridius: аватар, имя, @screen_name. */
@Composable
private fun ProfileHeader(profile: Profile?, onSettings: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Artwork(
            url = profile?.photoUrl,
            key = profile?.id?.toString() ?: "me",
            modifier = Modifier.size(52.dp).clip(CircleShape),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(profile?.name ?: stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            profile?.screenName?.let {
                Text("@$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
        }
    }
}

@Composable
private fun VkMixCard(selectedCount: Int, isStarting: Boolean, onPlay: () -> Unit, onConfigure: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val brush = Brush.linearGradient(listOf(accent, Color.Black.copy(alpha = 0.55f).compositeOver(accent), background))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White)
            Text("VK Микс", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }
        Text("Музыка под ваше настроение и вкус", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Text(
            "Плейлист, который подстраивается под ваши вкусы" + if (selectedCount > 0) " · фильтров: $selectedCount" else "",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onPlay,
                enabled = !isStarting,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                if (isStarting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                }
                Text("Слушать микс", Modifier.padding(start = 6.dp))
            }
            TextButton(onClick = onConfigure, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                Icon(Icons.Rounded.Tune, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Text("Настроить микс", Modifier.padding(start = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MixSettingsSheet(
    categories: List<MixCategory>,
    isLoading: Boolean,
    error: String?,
    selected: Map<String, List<String>>,
    onToggle: (String, String) -> Unit,
    onReset: () -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text("Настроить VK Микс", style = MaterialTheme.typography.titleLarge)
            Text(
                "Настройте параметры звучания микса под ваши предпочтения",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                isLoading -> LoadingBox()
                error != null -> MessageBox(error)
            }
            categories.forEach { category ->
                Text(category.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    category.options.forEach { option ->
                        val checked = option.id in selected[category.id].orEmpty()
                        FilterChip(
                            selected = checked,
                            onClick = { onToggle(category.id, option.id) },
                            label = { Text(option.title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            ) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Сбросить фильтры") }
                Button(onClick = onPlay, modifier = Modifier.weight(1f)) { Text("Слушать микс") }
            }
        }
    }
}
