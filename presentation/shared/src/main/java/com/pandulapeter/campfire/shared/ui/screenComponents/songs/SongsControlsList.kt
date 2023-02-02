package com.pandulapeter.campfire.shared.ui.screenComponents.songs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.catalogue.components.CheckboxItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.HeaderItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.RadioButtonItem
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings


@Composable
fun SongsControlsList(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    databases: List<Database>,
    unselectedDatabaseUrls: List<String>,
    shouldShowExplicitSongs: Boolean,
    showOnlyDownloadedSongs: Boolean,
    shouldShowSongsWithoutChords: Boolean,
    onDatabaseSelectedChanged: (Database, Boolean) -> Unit,
    onShouldShowExplicitSongsChanged: (Boolean) -> Unit,
    onShouldShowSongsWithoutChordsChanged: (Boolean) -> Unit,
    sortingMode: UserPreferences.SortingMode?,
    onSortingModeChanged: (UserPreferences.SortingMode) -> Unit,
    onShowOnlyDownloadedSongsChanged: (Boolean) -> Unit
) {
    Column(
        modifier = modifier.wrapContentSize().verticalScroll(rememberScrollState())
    ) {
        SongsSortingControlsList(
            uiStrings = uiStrings,
            sortingMode = sortingMode,
            onSortingModeChanged = onSortingModeChanged
        )
        SongsFilterControlsList(
            modifier = modifier,
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
    }
}

@Composable
fun SongsSortingControlsList(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    sortingMode: UserPreferences.SortingMode?,
    onSortingModeChanged: (UserPreferences.SortingMode) -> Unit
) = Column(
    modifier = modifier
) {
    HeaderItem(
        text = uiStrings.songsSortingMode,
        shouldUseLargePadding = false
    )
    RadioButtonItem(
        text = uiStrings.songsSortingModeByArtist,
        isChecked = sortingMode == UserPreferences.SortingMode.BY_ARTIST,
        onClick = { onSortingModeChanged(UserPreferences.SortingMode.BY_ARTIST) }
    )
    RadioButtonItem(
        text = uiStrings.songsSortingModeByTitle,
        isChecked = sortingMode == UserPreferences.SortingMode.BY_TITLE,
        onClick = { onSortingModeChanged(UserPreferences.SortingMode.BY_TITLE) }
    )
}

@Composable
fun SongsFilterControlsList(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    databases: List<Database>,
    unselectedDatabaseUrls: List<String>,
    shouldShowExplicitSongs: Boolean,
    shouldShowSongsWithoutChords: Boolean,
    showOnlyDownloadedSongs: Boolean,
    onDatabaseSelectedChanged: (Database, Boolean) -> Unit,
    onShouldShowExplicitSongsChanged: (Boolean) -> Unit,
    onShouldShowSongsWithoutChordsChanged: (Boolean) -> Unit,
    onShowOnlyDownloadedSongsChanged: (Boolean) -> Unit
) = Column(
    modifier = modifier
) {
    HeaderItem(
        text = uiStrings.songsFilters,
        shouldUseLargePadding = false
    )
    CheckboxItem(
        text = uiStrings.songsShowExplicit,
        isChecked = shouldShowExplicitSongs,
        onCheckedChanged = onShouldShowExplicitSongsChanged
    )
    CheckboxItem(
        text = uiStrings.songsShowWithoutChords,
        isChecked = shouldShowSongsWithoutChords,
        onCheckedChanged = onShouldShowSongsWithoutChordsChanged
    )
    CheckboxItem(
        text = uiStrings.showOnlyDownloadedSongs,
        isChecked = showOnlyDownloadedSongs,
        onCheckedChanged = onShowOnlyDownloadedSongsChanged
    )
    databases.filter { it.isEnabled }.forEach { database ->
        CheckboxItem(
            text = uiStrings.songsDatabaseFilter(database.name),
            isChecked = !unselectedDatabaseUrls.contains(database.url),
            onCheckedChanged = { onDatabaseSelectedChanged(database, it) }
        )
    }
}