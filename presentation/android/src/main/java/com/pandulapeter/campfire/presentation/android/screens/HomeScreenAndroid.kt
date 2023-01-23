package com.pandulapeter.campfire.presentation.android.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.screenComponents.home.HomeContentList
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun HomeScreenAndroid(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel = KoinJavaComponent.get(CampfireViewModel::class.java)
) {
    val isRefreshing = viewModel.shouldShowLoadingIndicator.collectAsState(false)
    val coroutineScope = rememberCoroutineScope()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing.value,
        onRefresh = { coroutineScope.launch { viewModel.onForceRefreshPressed() } }
    )
    val collections = viewModel.collections.collectAsState(emptyList())
    val songs = viewModel.songs.collectAsState(emptyList())

    LaunchedEffect(Unit) { viewModel.onInitialize() }

    Box(
        modifier = modifier.pullRefresh(pullRefreshState)
    ) {
        HomeContentList(
            modifier = Modifier.fillMaxHeight(),
            collections = collections.value,
            songs = songs.value,
            onCollectionClicked = viewModel::onCollectionClicked,
            onSongClicked = viewModel::onSongClicked
        )
        PullRefreshIndicator(
            modifier = Modifier.align(Alignment.TopCenter),
            refreshing = isRefreshing.value,
            state = pullRefreshState
        )
    }
}