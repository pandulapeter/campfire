package com.pandulapeter.campfire.presentation.ios.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.resources.UiConstants
import com.pandulapeter.campfire.shared.ui.screenComponents.setlists.SetlistsContentList
import com.pandulapeter.campfire.shared.ui.screenComponents.setlists.SetlistsControlsList

@Composable
internal fun SetlistsScreenIos(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    state: LazyListState,
    shouldUseExpandedUi: Boolean
) = if (shouldUseExpandedUi) {
    Row(
        modifier = modifier.fillMaxSize()
    ) {
        SetlistsContentListIos(
            modifier = Modifier.fillMaxWidth(UiConstants.VERTICAL_DIVIDER_RATIO),
            stateHolder = stateHolder,
            state = state,
            shouldUseExpandedUi = true
        )
        SetlistsControlsList(
            modifier = Modifier.fillMaxSize(),
            uiStrings = stateHolder.uiStrings.value,
            databases = stateHolder.databases.value,
            unselectedDatabaseUrls = stateHolder.userPreferences.value?.unselectedDatabaseUrls.orEmpty(),
            shouldShowExplicitSongs = stateHolder.userPreferences.value?.shouldShowExplicitSongs == true,
            shouldShowSongsWithoutChords = stateHolder.userPreferences.value?.shouldShowSongsWithoutChords == true,
            shouldAddFabPadding = true,
            showOnlyDownloadedSongs = stateHolder.userPreferences.value?.showOnlyDownloadedSongs == true,
            onDatabaseSelectedChanged = stateHolder::onDatabaseSelectedChanged,
            onShouldShowExplicitSongsChanged = stateHolder::onShouldShowExplicitSongsChanged,
            onShouldShowSongsWithoutChordsChanged = stateHolder::onShouldShowSongsWithoutChordsChanged,
            onShowOnlyDownloadedSongsChanged = stateHolder::onShowOnlyDownloadedSongsChanged
        )
    }
} else {
    SetlistsContentListIos(
        modifier = modifier,
        stateHolder = stateHolder,
        state = state,
        shouldUseExpandedUi = false
    )
}

@Composable
private fun SetlistsContentListIos(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    state: LazyListState,
    shouldUseExpandedUi: Boolean
) = SetlistsContentList(
    modifier = modifier,
    stateHolder = stateHolder,
    uiStrings = stateHolder.uiStrings.value,
    state = state,
    shouldAddFabPadding = !shouldUseExpandedUi,
    songs = stateHolder.songs.value,
    setlists = stateHolder.setlists.value,
    rawSongDetails = stateHolder.rawSongDetails.value,
    onSongClicked = stateHolder::onSongClicked
)