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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
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
                uiStrings = stateHolder.uiStrings.value,
                sortingMode = stateHolder.userPreferences.value?.sortingMode,
                shouldUseHeaders = stateHolder.query.value.isBlank(),
                pullRefreshState = pullRefreshState,
                isRefreshing = stateHolder.isRefreshing.value,
                songs = stateHolder.songs.value,
                rawSongDetails = stateHolder.rawSongDetails.value,
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
                uiStrings = stateHolder.uiStrings.value,
                databases = stateHolder.databases.value,
                unselectedDatabaseUrls = stateHolder.userPreferences.value?.unselectedDatabaseUrls.orEmpty(),
                shouldShowExplicitSongs = stateHolder.userPreferences.value?.shouldShowExplicitSongs == true,
                shouldShowSongsWithoutChords = stateHolder.userPreferences.value?.shouldShowSongsWithoutChords == true,
                onDatabaseSelectedChanged = stateHolder::onDatabaseSelectedChanged,
                onShouldShowExplicitSongsChanged = stateHolder::onShouldShowExplicitSongsChanged,
                onShouldShowSongsWithoutChordsChanged = stateHolder::onShouldShowSongsWithoutChordsChanged,
                sortingMode = stateHolder.userPreferences.value?.sortingMode,
                onSortingModeChanged = stateHolder::onSortingModeChanged
            )
        }
    } else {
        SongsContentListWithPullRefresh(
            modifier = modifier,
            uiStrings = stateHolder.uiStrings.value,
            sortingMode = stateHolder.userPreferences.value?.sortingMode,
            shouldUseHeaders = stateHolder.query.value.isBlank(),
            pullRefreshState = pullRefreshState,
            isRefreshing = stateHolder.isRefreshing.value,
            songs = stateHolder.songs.value,
            rawSongDetails = stateHolder.rawSongDetails.value,
            onSongClicked = stateHolder::onSongClicked,
            lazyListState = lazyListState
        )
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun SongsContentListWithPullRefresh(
    modifier: Modifier,
    uiStrings: CampfireStrings,
    pullRefreshState: PullRefreshState,
    isRefreshing: Boolean,
    sortingMode: UserPreferences.SortingMode?,
    shouldUseHeaders: Boolean,
    songs: List<Song>,
    rawSongDetails: Map<String, RawSongDetails>,
    onSongClicked: (Song) -> Unit,
    lazyListState: LazyListState
) = Box(
    modifier = modifier.pullRefresh(pullRefreshState)
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    SongsContentList(
        modifier = Modifier.fillMaxHeight(),
        uiStrings = uiStrings,
        sortingMode = sortingMode,
        shouldUseHeaders = shouldUseHeaders,
        songs = songs,
        rawSongDetails = rawSongDetails,
        onSongClicked = {
            keyboardController?.hide()
            onSongClicked(it)
        },
        state = lazyListState
    )
    PullRefreshIndicator(
        modifier = Modifier.align(Alignment.TopCenter),
        refreshing = isRefreshing,
        state = pullRefreshState
    )
}