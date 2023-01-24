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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.screenComponents.songs.SongsContentList
import com.pandulapeter.campfire.shared.ui.screenComponents.songs.SongsControlsList

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun SongsScreenAndroid(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    shouldUseExpandedUi: Boolean,
    pullRefreshState: PullRefreshState,
    lazyListState: LazyListState
) {

    if (shouldUseExpandedUi) {
        Row(
            modifier = modifier
        ) {
            SongsContentListWithPullRefresh(
                modifier = Modifier.fillMaxWidth(0.65f),
                pullRefreshState = pullRefreshState,
                isRefreshing = stateHolder.isRefreshing.value,
                songs = stateHolder.songs.value,
                onSongClicked = stateHolder::onSongClicked,
                lazyListState = lazyListState
            )
            Spacer(
                modifier = Modifier.width(8.dp)
            )
            SongsControlsList(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 8.dp),
                query = stateHolder.query.value,
                databases = stateHolder.databases.value,
                unselectedDatabaseUrls = stateHolder.userPreferences.value?.unselectedDatabaseUrls.orEmpty(),
                shouldShowExplicitSongs = stateHolder.userPreferences.value?.shouldShowExplicitSongs == true,
                shouldShowSongsWithoutChords = stateHolder.userPreferences.value?.shouldShowSongsWithoutChords == true,
                onDatabaseEnabledChanged = { database, isEnabled -> stateHolder.onDatabaseEnabledChanged(stateHolder.databases.value, database, isEnabled) },
                onDatabaseSelectedChanged = { database, isEnabled ->
                    stateHolder.userPreferences.value?.let { userPreferences ->
                        stateHolder.onDatabaseSelectedChanged(userPreferences, database, isEnabled)
                    }
                },
                onShouldShowExplicitSongsChanged = { shouldShowExplicitSongs ->
                    stateHolder.userPreferences.value?.let { userPreferences ->
                        stateHolder.onShouldShowExplicitSongsChanged(userPreferences, shouldShowExplicitSongs)
                    }
                },
                onShouldShowSongsWithoutChordsChanged = { shouldShowSongsWithoutChords ->
                    stateHolder.userPreferences.value?.let { userPreferences ->
                        stateHolder.onShouldShowSongsWithoutChordsChanged(userPreferences, shouldShowSongsWithoutChords)
                    }
                },
                onForceRefreshPressed = stateHolder::onForceRefreshTriggered,
                onDeleteLocalDataPressed = stateHolder::onDeleteLocalDataPressed,
                onQueryChanged = stateHolder::onQueryChanged
            )
        }
    } else {
        SongsContentListWithPullRefresh(
            modifier = modifier,
            pullRefreshState = pullRefreshState,
            isRefreshing = stateHolder.isRefreshing.value,
            songs = stateHolder.songs.value,
            onSongClicked = stateHolder::onSongClicked,
            lazyListState = lazyListState
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SongsContentListWithPullRefresh(
    modifier: Modifier,
    pullRefreshState: PullRefreshState,
    isRefreshing: Boolean,
    songs: List<Song>,
    onSongClicked: (Song) -> Unit,
    lazyListState: LazyListState
) = Box(
    modifier = modifier.pullRefresh(pullRefreshState)
) {
    SongsContentList(
        modifier = Modifier.fillMaxHeight(),
        songs = songs,
        onSongClicked = onSongClicked,
        state = lazyListState
    )
    PullRefreshIndicator(
        modifier = Modifier.align(Alignment.TopCenter),
        refreshing = isRefreshing,
        state = pullRefreshState
    )
}