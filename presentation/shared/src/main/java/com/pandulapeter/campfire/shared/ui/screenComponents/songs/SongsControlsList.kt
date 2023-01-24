package com.pandulapeter.campfire.shared.ui.screenComponents.songs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.shared.ui.catalogue.components.CheckboxItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.ClickableControlItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.HeaderItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.SearchItem
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongsControlsList(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    query: String,
    databases: List<Database>,
    unselectedDatabaseUrls: List<String>,
    shouldShowExplicitSongs: Boolean,
    shouldShowSongsWithoutChords: Boolean,
    onDatabaseEnabledChanged: (Database, Boolean) -> Unit,
    onDatabaseSelectedChanged: (Database, Boolean) -> Unit,
    onShouldShowExplicitSongsChanged: (Boolean) -> Unit,
    onShouldShowSongsWithoutChordsChanged: (Boolean) -> Unit,
    onForceRefreshPressed: () -> Unit,
    onDeleteLocalDataPressed: () -> Unit,
    onQueryChanged: (String) -> Unit
) = LazyColumn(
    modifier = modifier
) {
    if (databases.isNotEmpty()) {
        item(key = "header_databases") {
            HeaderItem(
                modifier = Modifier.animateItemPlacement(),
                text = uiStrings.songsAllDatabases
            )
        }
        itemsIndexed(
            items = databases,
            key = { _, database -> "database_${database.url}" }
        ) { _, database ->
            CheckboxItem(
                modifier = Modifier.animateItemPlacement(),
                text = database.name,
                isChecked = database.isEnabled,
                onCheckedChanged = { onDatabaseEnabledChanged(database, it) }
            )
        }
        val enabledDatabases = databases.filter { it.isEnabled }
        if (enabledDatabases.isNotEmpty()) {
            item(key = "header_filters") {
                HeaderItem(
                    modifier = Modifier.animateItemPlacement(),
                    text = uiStrings.songsFilters
                )
            }
            item(key = "filter_search") {
                SearchItem(
                    query = query,
                    uiStrings = uiStrings,
                    onQueryChanged = onQueryChanged
                )
            }
            item(key = "filter_explicit") {
                CheckboxItem(
                    modifier = Modifier.animateItemPlacement(),
                    text = uiStrings.songsShowExplicit,
                    isChecked = shouldShowExplicitSongs,
                    onCheckedChanged = onShouldShowExplicitSongsChanged
                )
            }
            item(key = "filter_songs_without_chords") {
                CheckboxItem(
                    modifier = Modifier.animateItemPlacement(),
                    text = uiStrings.songsShowWithoutChords,
                    isChecked = shouldShowSongsWithoutChords,
                    onCheckedChanged = onShouldShowSongsWithoutChordsChanged
                )
            }
            itemsIndexed(
                items = enabledDatabases,
                key = { _, database -> "filter_database_${database.url}" }
            ) { _, database ->
                CheckboxItem(
                    modifier = Modifier.animateItemPlacement(),
                    text = uiStrings.songsDatabaseFilter(database.name),
                    isChecked = !unselectedDatabaseUrls.contains(database.url),
                    onCheckedChanged = { onDatabaseSelectedChanged(database, it) }
                )
            }
        }
        item(key = "header_actions") {
            HeaderItem(
                modifier = Modifier.animateItemPlacement(),
                text = uiStrings.songsActions
            )
        }
        item(key = "force_refresh") {
            ClickableControlItem(
                modifier = Modifier.animateItemPlacement(),
                text = uiStrings.songsRefresh,
                onClick = { onForceRefreshPressed() }
            )
        }
        item(key = "delete_local_data") {
            ClickableControlItem(
                modifier = Modifier.animateItemPlacement(),
                text = uiStrings.songsDeleteLocalData,
                onClick = { onDeleteLocalDataPressed() }
            )
        }
        item(key = "spacer") {
            Spacer(
                modifier = Modifier.height(8.dp)
            )
        }
    }
}