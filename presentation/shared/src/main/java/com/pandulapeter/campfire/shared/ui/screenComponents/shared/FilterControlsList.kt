package com.pandulapeter.campfire.shared.ui.screenComponents.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.shared.ui.catalogue.components.CheckboxItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.HeaderItem
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings


@Composable
fun FilterControlsList(
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
//    CheckboxItem(
//        text = uiStrings.showOnlyDownloadedSongs,
//        isChecked = showOnlyDownloadedSongs,
//        onCheckedChanged = onShowOnlyDownloadedSongsChanged
//    )
    databases.filter { it.isEnabled }.forEach { database ->
        CheckboxItem(
            text = uiStrings.songsDatabaseFilter(database.name),
            isChecked = !unselectedDatabaseUrls.contains(database.url),
            onCheckedChanged = { onDatabaseSelectedChanged(database, it) }
        )
    }
}