package com.pandulapeter.campfire.presentation.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.screenComponents.setlists.SetlistsContentList

@Composable
internal fun SetlistsScreensDesktop(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    state: LazyListState,
    songs: List<Song>,
    setlists: List<Setlist>,
    rawSongDetails: Map<String, RawSongDetails>,
    onSongClicked: (Song) -> Unit
) = SetlistsContentList(
    modifier = modifier.padding(8.dp),
    uiStrings = stateHolder.uiStrings.value,
    state = state,
    songs = songs,
    setlists = setlists,
    rawSongDetails = rawSongDetails,
    onSongClicked = onSongClicked
)