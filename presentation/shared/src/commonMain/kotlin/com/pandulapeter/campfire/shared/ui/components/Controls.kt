package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.resources.Res
import com.pandulapeter.campfire.shared.resources.filters
import com.pandulapeter.campfire.shared.resources.songs_database_filter
import com.pandulapeter.campfire.shared.resources.songs_show_explicit
import com.pandulapeter.campfire.shared.resources.songs_show_without_chords
import com.pandulapeter.campfire.shared.resources.songs_sorting_mode
import com.pandulapeter.campfire.shared.resources.songs_sorting_mode_by_artist
import com.pandulapeter.campfire.shared.resources.songs_sorting_mode_by_title
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.localization.stringResource

/**
 * Sorting and filter controls of the song list, shown in a side panel on expanded windows and in a bottom sheet
 * otherwise.
 */
@Composable
internal fun SongsControls(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    shouldIncludeSorting: Boolean,
    contentPadding: PaddingValues = PaddingValues()
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val databases by viewModel.databases.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding)
    ) {
        if (shouldIncludeSorting) {
            SettingsSectionTitle(text = stringResource(Res.string.songs_sorting_mode))
            SegmentedChoice(
                options = listOf(
                    UserPreferences.SortingMode.BY_ARTIST to stringResource(Res.string.songs_sorting_mode_by_artist),
                    UserPreferences.SortingMode.BY_TITLE to stringResource(Res.string.songs_sorting_mode_by_title)
                ),
                selected = userPreferences?.sortingMode,
                onSelected = viewModel::setSortingMode
            )
        }
        SettingsSectionTitle(text = stringResource(Res.string.filters))
        CheckboxListItem(
            title = stringResource(Res.string.songs_show_explicit),
            isChecked = userPreferences?.shouldShowExplicitSongs == true,
            onCheckedChange = viewModel::setShouldShowExplicitSongs
        )
        CheckboxListItem(
            title = stringResource(Res.string.songs_show_without_chords),
            isChecked = userPreferences?.shouldShowSongsWithoutChords == true,
            onCheckedChange = viewModel::setShouldShowSongsWithoutChords
        )
        databases.filter { it.isEnabled }.forEach { database ->
            CheckboxListItem(
                title = stringResource(Res.string.songs_database_filter, database.name),
                isChecked = userPreferences?.unselectedDatabaseUrls?.contains(database.url) == false,
                onCheckedChange = { viewModel.setDatabaseSelected(database, it) }
            )
        }
    }
}
