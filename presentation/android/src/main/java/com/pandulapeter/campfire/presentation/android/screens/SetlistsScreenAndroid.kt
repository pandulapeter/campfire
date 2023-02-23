package com.pandulapeter.campfire.presentation.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.screenComponents.setlists.SetlistsContentList
import org.burnoutcrew.reorderable.ReorderableLazyListState

@Composable
internal fun SetlistsScreenAndroid(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    state: ReorderableLazyListState,
    shouldUseExpandedUi: Boolean
) = SetlistsContentList(
    modifier = modifier,
    stateHolder = stateHolder,
    uiStrings = stateHolder.uiStrings.value,
    state = state,
    shouldUseExpandedUi = shouldUseExpandedUi,
    songs = stateHolder.songs.value,
    setlists = stateHolder.setlists.value,
    rawSongDetails = stateHolder.rawSongDetails.value,
    onSongClicked = stateHolder::onSongClicked
)