package com.pandulapeter.campfire.shared.ui.screenComponents.setlists

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import com.pandulapeter.campfire.shared.ui.screenComponents.shared.FilterControlsList


@Composable
fun SetlistsControlsList(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    databases: List<Database>,
    unselectedDatabaseUrls: List<String>,
    shouldShowExplicitSongs: Boolean,
    showOnlyDownloadedSongs: Boolean,
    shouldShowSongsWithoutChords: Boolean,
    shouldAddFabPadding: Boolean,
    onDatabaseSelectedChanged: (Database, Boolean) -> Unit,
    onShouldShowExplicitSongsChanged: (Boolean) -> Unit,
    onShouldShowSongsWithoutChordsChanged: (Boolean) -> Unit,
    onShowOnlyDownloadedSongsChanged: (Boolean) -> Unit
) = FilterControlsList(
    modifier = modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = if (shouldAddFabPadding) 80.dp else 0.dp),
    uiStrings = uiStrings,
    databases = databases,
    unselectedDatabaseUrls = unselectedDatabaseUrls,
    shouldShowExplicitSongs = shouldShowExplicitSongs,
    showOnlyDownloadedSongs = showOnlyDownloadedSongs,
    shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
    onDatabaseSelectedChanged = onDatabaseSelectedChanged,
    onShouldShowExplicitSongsChanged = onShouldShowExplicitSongsChanged,
    onShouldShowSongsWithoutChordsChanged = onShouldShowSongsWithoutChordsChanged,
    onShowOnlyDownloadedSongsChanged = onShowOnlyDownloadedSongsChanged
)