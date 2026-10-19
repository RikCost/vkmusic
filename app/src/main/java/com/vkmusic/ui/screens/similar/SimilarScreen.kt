package com.vkmusic.ui.screens.similar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Track
import com.vkmusic.ui.components.BackTopBar
import com.vkmusic.ui.components.TrackSourcePage
import com.vkmusic.ui.components.TrackSourceViewModel
import com.vkmusic.ui.navigation.SimilarRoute
import com.vkmusic.ui.screens.bottomOnly
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SimilarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: VkAudioRepository,
) : TrackSourceViewModel() {

    val route = savedStateHandle.toRoute<SimilarRoute>()

    init {
        refresh()
    }

    override suspend fun load(): List<Track> = api.getSimilar(route.ownerId, route.audioId)
}

@Composable
fun SimilarScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSimilar: (Track) -> Unit,
    viewModel: SimilarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = "Похожие", subtitle = viewModel.route.title, onBack = onBack)
        TrackSourcePage(
            state = state,
            onRefresh = viewModel::refresh,
            onSimilar = onSimilar,
            contentPadding = contentPadding.bottomOnly(),
            emptyText = "Похожих треков не нашлось",
        )
    }
}
