package com.pandulapeter.campfire.presentation.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
) = Row(
    modifier = modifier.fillMaxSize()
) {
    Box(
        modifier = Modifier.fillMaxWidth(0.55f),
    ) {
        SongsContentList(
            modifier = Modifier.fillMaxSize().padding(end = 8.dp),
            state = lazyListState,
            songs = stateHolder.songs.value,
            onSongClicked = stateHolder::onSongClicked
        )
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(
                scrollState = lazyListState
            )
        )
    }
    Spacer(
        modifier = Modifier.width(8.dp)
    )
    SongsControlsList(
        modifier = Modifier.fillMaxSize(),
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