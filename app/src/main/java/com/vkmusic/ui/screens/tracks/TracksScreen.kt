package com.vkmusic.ui.screens.tracks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vkmusic.data.model.Track
import com.vkmusic.ui.components.AboutDialog
import com.vkmusic.ui.components.TrackSourcePage
import com.vkmusic.ui.screens.bottomOnly

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    contentPadding: PaddingValues,
    onSimilar: (Track) -> Unit,
    onSignOut: () -> Unit,
    viewModel: TracksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAbout by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Моя музыка") },
            actions = {
                IconButton(onClick = { showAbout = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = "О приложении")
                }
                IconButton(onClick = onSignOut) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Выйти")
                }
            },
        )
        TrackSourcePage(
            state = state,
            onRefresh = viewModel::refresh,
            onSimilar = onSimilar,
            contentPadding = contentPadding.bottomOnly(),
            emptyText = "В вашей музыке пока нет треков",
        )
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}
