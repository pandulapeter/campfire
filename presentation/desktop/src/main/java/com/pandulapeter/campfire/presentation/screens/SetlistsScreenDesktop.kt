package com.pandulapeter.campfire.presentation.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.resources.UiConstants
import com.pandulapeter.campfire.shared.ui.screenComponents.setlists.SetlistsContentList
import com.pandulapeter.campfire.shared.ui.screenComponents.setlists.SetlistsControlsList
import org.burnoutcrew.reorderable.ReorderableLazyListState

@Composable
internal fun SetlistsScreensDesktop(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    state: ReorderableLazyListState,
    shouldUseExpandedUi: Boolean
) = if (shouldUseExpandedUi) {
    Row(
        modifier = modifier.fillMaxSize()
    ) {
        SetlistsContentListWithScrollbar(
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
    SetlistsContentListWithScrollbar(
        stateHolder = stateHolder,
        state = state,
        shouldUseExpandedUi = false
    )
}

@Composable
private fun SetlistsContentListWithScrollbar(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    state: ReorderableLazyListState,
    shouldUseExpandedUi: Boolean
) = Box(
    modifier = modifier
) {
    SetlistsContentList(
        modifier = Modifier.fillMaxSize().padding(end = 8.dp),
        stateHolder = stateHolder,
        uiStrings = stateHolder.uiStrings.value,
        state = state,
        shouldAddFabPadding = !shouldUseExpandedUi,
        songs = stateHolder.songs.value,
        setlists = stateHolder.setlists.value,
        rawSongDetails = stateHolder.rawSongDetails.value,
        onSongClicked = stateHolder::onSongClicked
    )
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(
            scrollState = state.listState
        )
    )
}