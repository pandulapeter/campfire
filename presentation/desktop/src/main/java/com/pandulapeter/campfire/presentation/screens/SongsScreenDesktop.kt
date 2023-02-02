package com.pandulapeter.campfire.presentation.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.screenComponents.songs.SongsContentList
import com.pandulapeter.campfire.shared.ui.screenComponents.songs.SongsControlsList

@Composable
internal fun SongsScreenDesktop(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    lazyListState: LazyListState,
    shouldUseExpandedUi: Boolean
) = if (shouldUseExpandedUi) {
    Row(
        modifier = modifier.fillMaxSize()
    ) {
        SongsContentListWithScrollBar(
            modifier = Modifier.fillMaxWidth(0.55f),
            stateHolder = stateHolder,
            lazyListState = lazyListState
        )
        SongsControlsList(
            modifier = Modifier.fillMaxSize(),
            uiStrings = stateHolder.uiStrings.value,
            databases = stateHolder.databases.value,
            unselectedDatabaseUrls = stateHolder.userPreferences.value?.unselectedDatabaseUrls.orEmpty(),
            shouldShowExplicitSongs = stateHolder.userPreferences.value?.shouldShowExplicitSongs == true,
            shouldShowSongsWithoutChords = stateHolder.userPreferences.value?.shouldShowSongsWithoutChords == true,
            showOnlyDownloadedSongs = stateHolder.userPreferences.value?.showOnlyDownloadedSongs == true,
            onDatabaseSelectedChanged = stateHolder::onDatabaseSelectedChanged,
            onShouldShowExplicitSongsChanged = stateHolder::onShouldShowExplicitSongsChanged,
            onShouldShowSongsWithoutChordsChanged = stateHolder::onShouldShowSongsWithoutChordsChanged,
            sortingMode = stateHolder.userPreferences.value?.sortingMode,
            onSortingModeChanged = stateHolder::onSortingModeChanged,
            onShowOnlyDownloadedSongsChanged = stateHolder::onShowOnlyDownloadedSongsChanged
        )
    }
} else {
    SongsContentListWithScrollBar(
        stateHolder = stateHolder,
        lazyListState = lazyListState
    )
}

@Composable
private fun SongsContentListWithScrollBar(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    lazyListState: LazyListState
) = Box(
    modifier = modifier,
) {
    SongsContentList(
        modifier = Modifier.fillMaxSize().padding(end = 8.dp),
        uiStrings = stateHolder.uiStrings.value,
        sortingMode = stateHolder.userPreferences.value?.sortingMode,
        shouldUseHeaders = stateHolder.query.value.isBlank(),
        state = lazyListState,
        songs = stateHolder.songs.value,
        rawSongDetails = stateHolder.rawSongDetails.value,
        onSongClicked = stateHolder::onSongClicked
    )
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(
            scrollState = lazyListState
        )
    )
}