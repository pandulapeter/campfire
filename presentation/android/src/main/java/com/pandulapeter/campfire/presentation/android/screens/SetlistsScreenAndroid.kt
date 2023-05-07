package com.pandulapeter.campfire.presentation.android.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.resources.UiConstants
import com.pandulapeter.campfire.shared.ui.screenComponents.setlists.SetlistsContentList
import com.pandulapeter.campfire.shared.ui.screenComponents.setlists.SetlistsControlsList
import org.burnoutcrew.reorderable.ReorderableLazyListState

@Composable
internal fun SetlistsScreenAndroid(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    state: ReorderableLazyListState,
    shouldUseExpandedUi: Boolean
) = if (shouldUseExpandedUi) {
    Row(
        modifier = modifier.fillMaxSize()
    ) {
        SetlistsContentListAndroid(
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
    SetlistsContentListAndroid(
        modifier = modifier,
        stateHolder = stateHolder,
        state = state,
        shouldUseExpandedUi = false
    )
}

@Composable
private fun SetlistsContentListAndroid(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    state: ReorderableLazyListState,
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