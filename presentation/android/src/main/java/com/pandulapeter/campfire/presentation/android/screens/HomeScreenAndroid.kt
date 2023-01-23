package com.pandulapeter.campfire.presentation.android.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.screenComponents.home.HomeContentList
import com.pandulapeter.campfire.shared.ui.screenComponents.home.HomeControlsList
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun HomeScreenAndroid(
    modifier: Modifier = Modifier,
    shouldUseExpandedUi: Boolean,
    viewModel: CampfireViewModel = KoinJavaComponent.get(CampfireViewModel::class.java)
) {
    val isRefreshing = viewModel.shouldShowLoadingIndicator.collectAsState(false)
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing.value,
        onRefresh = { coroutineScope.launch { viewModel.onForceRefreshPressed() } }
    )
    val query = viewModel.query.collectAsState("")
    val databases = viewModel.databases.collectAsState(emptyList())
    val collections = viewModel.collections.collectAsState(emptyList())
    val songs = viewModel.songs.collectAsState(emptyList())
    val dataState = viewModel.dataState.collectAsState("Uninitialized")
    val userPreferences = viewModel.userPreferences.collectAsState(null)

    LaunchedEffect(Unit) { viewModel.onInitialize() }

    if (shouldUseExpandedUi) {
        Row(
            modifier = modifier
        ) {
            HomeContentListWithPullRefresh(
                modifier = Modifier.fillMaxWidth(0.65f),
                pullRefreshState = pullRefreshState,
                isRefreshing = isRefreshing.value,
                collections = collections.value,
                songs = songs.value,
                onCollectionClicked = viewModel::onCollectionClicked,
                onSongClicked = viewModel::onSongClicked,
                lazyListState = lazyListState
            )
            Spacer(
                modifier = Modifier.width(8.dp)
            )
            HomeControlsList(
                modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                state = dataState.value,
                query = query.value,
                databases = databases.value,
                unselectedDatabaseUrls = userPreferences.value?.unselectedDatabaseUrls.orEmpty(),
                shouldShowExplicitSongs = userPreferences.value?.shouldShowExplicitSongs == true,
                shouldShowSongsWithoutChords = userPreferences.value?.shouldShowSongsWithoutChords == true,
                onDatabaseEnabledChanged = { database, isEnabled ->
                    coroutineScope.launch {
                        viewModel.onDatabaseEnabledChanged(databases.value, database, isEnabled)
                    }
                },
                onDatabaseSelectedChanged = { database, isEnabled ->
                    userPreferences.value?.let { userPreferences ->
                        coroutineScope.launch { viewModel.onDatabaseSelectedChanged(userPreferences, database, isEnabled) }
                    }
                },
                onShouldShowExplicitSongsChanged = { shouldShowExplicitSongs ->
                    userPreferences.value?.let { userPreferences ->
                        coroutineScope.launch { viewModel.onShouldShowExplicitSongsChanged(userPreferences, shouldShowExplicitSongs) }
                    }
                },
                onShouldShowSongsWithoutChordsChanged = { shouldShowSongsWithoutChords ->
                    userPreferences.value?.let { userPreferences ->
                        coroutineScope.launch { viewModel.onShouldShowSongsWithoutChordsChanged(userPreferences, shouldShowSongsWithoutChords) }
                    }
                },
                onForceRefreshPressed = { coroutineScope.launch { viewModel.onForceRefreshPressed() } },
                onDeleteLocalDataPressed = { coroutineScope.launch { viewModel.onDeleteLocalDataPressed() } },
                onQueryChanged = viewModel::onQueryChanged
            )
        }
    } else {
        HomeContentListWithPullRefresh(
            modifier = modifier,
            pullRefreshState = pullRefreshState,
            isRefreshing = isRefreshing.value,
            collections = collections.value,
            songs = songs.value,
            onCollectionClicked = viewModel::onCollectionClicked,
            onSongClicked = viewModel::onSongClicked,
            lazyListState = lazyListState
        )
        // TODO: Add controls as bottom sheet
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun HomeContentListWithPullRefresh(
    modifier: Modifier,
    pullRefreshState: PullRefreshState,
    isRefreshing: Boolean,
    collections: List<Collection>,
    songs: List<Song>,
    onCollectionClicked: (Collection) -> Unit,
    onSongClicked: (Song) -> Unit,
    lazyListState: LazyListState
) = Box(
    modifier = modifier.pullRefresh(pullRefreshState)
) {
    HomeContentList(
        modifier = Modifier.fillMaxHeight(),
        collections = collections,
        songs = songs,
        onCollectionClicked = onCollectionClicked,
        onSongClicked = onSongClicked,
        state = lazyListState
    )
    PullRefreshIndicator(
        modifier = Modifier.align(Alignment.TopCenter),
        refreshing = isRefreshing,
        state = pullRefreshState
    )
}