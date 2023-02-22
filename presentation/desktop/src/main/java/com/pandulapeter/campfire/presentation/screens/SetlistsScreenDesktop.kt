package com.pandulapeter.campfire.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.screenComponents.setlists.SetlistsContentList
import org.burnoutcrew.reorderable.ReorderableLazyListState

@Composable
internal fun SetlistsScreensDesktop(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    state: ReorderableLazyListState,
    songs: List<Song>,
    setlists: List<Setlist>,
    rawSongDetails: Map<String, RawSongDetails>,
    onSongClicked: (Song) -> Unit
) = SetlistsContentList(
    modifier = modifier,
    uiStrings = stateHolder.uiStrings.value,
    state = state,
    songs = songs,
    setlists = setlists,
    rawSongDetails = rawSongDetails,
    onSongClicked = onSongClicked
)