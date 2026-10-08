/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.setlists_choose_songs
import com.pandulapeter.campfire.presentation.resources.songs_empty_title
import com.pandulapeter.campfire.presentation.resources.songs_no_search_results
import com.pandulapeter.campfire.presentation.resources.songs_search
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.search.PickerFilterOptions
import com.pandulapeter.campfire.presentation.ui.search.songPickerMatches
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.CountedFilterChip
import com.pandulapeter.campfire.presentation.ui.components.SortableChipRow
import com.pandulapeter.campfire.presentation.ui.components.ChecklistOrder
import com.pandulapeter.campfire.presentation.ui.components.checklistItems
import com.pandulapeter.campfire.presentation.ui.components.rememberChecklistOrder
import com.pandulapeter.campfire.presentation.ui.components.SongSortMenu
import com.pandulapeter.campfire.presentation.ui.components.languageLabel
import com.pandulapeter.campfire.presentation.ui.components.languageName
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.orderedBy
import org.jetbrains.compose.resources.painterResource

/**
 * Every song in the library with a box each, which is how a setlist is filled from its own side: the setlist picker
 * puts one song into any number of setlists, and this puts any number of songs into one setlist.
 *
 * What is ticked is held here rather than read back from the setlist, and each tick writes that one song in or out
 * ([CampfireViewModel.setSetlistSong]): a list of songs is ticked faster than a write comes back through the
 * library, and boxes that followed the library would each flick back for the length of a round trip. A song ticked
 * here goes to the end of the setlist, so a setlist built from nothing is in the order its songs were picked, and an
 * entry whose file has gone missing stays in it, since it is not listed here to be unticked.
 *
 * The songs in the setlist lead the list as a selected group, a divider separating them from the rest in the selected
 * sorting order ([ChecklistOrder]). A song ticked here joins the end of the group as a copy, in the order it was
 * ticked, which is the order it goes into the setlist in, while the row itself stays where it was tapped.
 *
 * The list can be narrowed by the library's languages and tags as well as by the search ([PickerFilters]). Those are
 * the picker's own rather than following the songs screen's filters. Tag and language selections survive reopening
 * the sheet for the current app run; search starts empty each time.
 */
@Composable
internal fun SongPicker(
    viewModel: CampfireViewModel,
    dialog: DialogType.SongPicker,
) {
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val setlist = setlists.firstOrNull { it.fileName == dialog.setlist.fileName } ?: dialog.setlist
    // Seeded from the setlist as the library has it and saved, rather than from the snapshot the dialog was opened
    // with: the dialog outlives a recreated Activity, and a selection seeded again from that snapshot would show every
    // song ticked before it unticked.
    val initialSongFileNames = rememberSaveable(setlist.fileName) { setlist.entries.map { it.songFileName } }
    var selectedSongFileNames by rememberSaveable(setlist.fileName) { mutableStateOf(initialSongFileNames) }
    var query by rememberSaveable { mutableStateOf("") }
    val selectedTags by viewModel.songPickerSelectedTags.collectAsStateWithLifecycle()
    val selectedLanguages by viewModel.songPickerSelectedLanguages.collectAsStateWithLifecycle()
    // Sorted, normalized for the search and counted for the chips by the view model, once per library rather than as
    // the sheet opens or on every keystroke, since the search runs over every song on every character typed and the
    // sheet's first frames are its slide up.
    val pickerSongs by viewModel.pickerSongs.collectAsStateWithLifecycle()
    val pickableSongs = pickerSongs.list
    val filters by viewModel.songPickerFilters.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    // Only what the chips still offer narrows the list: a tag that left the library while the sheet was open would
    // otherwise keep hiding every song with no chip left to turn it off.
    val activeTags = remember(filters, selectedTags) {
        selectedTags.filterTo(mutableSetOf()) { selected -> filters.tags.any { it.name.lowercase() == selected } }
    }
    val activeLanguages = remember(filters, selectedLanguages) {
        selectedLanguages.filterTo(mutableSetOf()) { selected -> filters.languages.any { it.code == selected } }
    }
    val isFiltered = activeTags.isNotEmpty() || activeLanguages.isNotEmpty()
    // Several values of one group widen the list and the two groups narrow each other, which is what the songs
    // screen's filters do by default: "Hungarian or English, and Christmas".
    val matches = remember(pickableSongs, query, activeTags, activeLanguages) {
        songPickerMatches(
            songs = pickableSongs,
            normalizedQuery = viewModel.normalizeForSearch(query),
            activeTags = activeTags,
            activeLanguages = activeLanguages,
        )
    }
    // An entry whose file has gone missing is not listed, so it is neither unticked here nor counted as ticked.
    val checkedSongKeys = remember(selectedSongFileNames, pickerSongs) {
        selectedSongFileNames.filterTo(mutableSetOf()) { it in pickerSongs.byFileName }
    }
    val refreshKey = listOf(userPreferences?.sortingMode, query, activeTags, activeLanguages)
    val songOrder = rememberChecklistOrder(checkedSongKeys, refreshKey)
    // Keep chip retention outside the lazy header, so changes reset it even while the header is off screen.
    val chipRefreshKey = query to userPreferences?.sortingMode
    val tagChipOrder = rememberChecklistOrder(activeTags, chipRefreshKey to userPreferences?.tagSortingMode)
    val languageChipOrder = rememberChecklistOrder(
        activeLanguages,
        listOf(chipRefreshKey, userPreferences?.languageSortingMode, currentLanguage.value.code),
    )
    CampfireBottomSheet(
        title = stringResource(Res.string.setlists_choose_songs),
        subtitle = setlist.title,
        actions = { SongSortMenu(viewModel = viewModel) },
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        PickerSearchField(
            query = query,
            placeholder = stringResource(Res.string.songs_search),
            onQueryChange = { query = it },
        )
        PickerList(
            contentPadding = contentPadding,
            refreshKey = refreshKey,
            contents = matches,
            checklistLayout = remember(matches, songOrder) { songOrder.layout(matches) { it.song.fileName } },
            noResultsText = when {
                // Reached from an empty setlist's "Choose songs" in an empty library, which the setlists screen lists as well.
                songs.isEmpty() -> stringResource(Res.string.songs_empty_title)
                matches.isEmpty() && (query.isNotBlank() || isFiltered) -> stringResource(Res.string.songs_no_search_results)
                else -> null
            },
            revealRowsKey = query.takeIf { it.isNotBlank() },
            header = if (filters.tags.isEmpty() && filters.languages.isEmpty()) null else {
                {
                    PickerFilters(
                        filters = filters,
                        selectedTags = activeTags,
                        selectedLanguages = activeLanguages,
                        tagOrder = tagChipOrder,
                        languageOrder = languageChipOrder,
                        refreshKey = chipRefreshKey,
                        tagSortingMode = userPreferences?.tagSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE,
                        languageSortingMode = userPreferences?.languageSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE,
                        onTagClicked = viewModel::toggleSongPickerTag,
                        onLanguageClicked = viewModel::toggleSongPickerLanguage,
                        onTagSortingModeSelected = viewModel::setTagSortingMode,
                        onLanguageSortingModeSelected = viewModel::setLanguageSortingMode,
                    )
                }
            },
        ) { listState ->
            checklistItems(
                items = matches,
                order = songOrder,
                key = { it.song.fileName },
                listState = listState,
            ) { pickableSong ->
                val fileName = pickableSong.song.fileName
                CheckboxListItem(
                    modifier = listItemAnimation(listState),
                    title = pickableSong.song.title,
                    description = pickableSong.song.artist.ifBlank { null },
                    isChecked = fileName in selectedSongFileNames,
                    coverArtUrl = pickableSong.song.coverArtUrl?.takeIf { userPreferences?.isCoverArtEnabled == true },
                    onCheckedChange = { isChecked ->
                        selectedSongFileNames = if (isChecked) selectedSongFileNames + fileName else selectedSongFileNames - fileName
                        viewModel.setSetlistSong(setlistFileName = setlist.fileName, songFileName = fileName, isTicked = isChecked)
                    },
                )
            }
        }
    }
}

/**
 * The tags of the library as a row of chips at the top of the [SongPicker]'s list, and its languages as a second row
 * under them, each where there are any to offer. Rows that scroll sideways rather than the wrapping groups of the songs
 * screen's filters: a library can carry a hundred tags, and the sheet is there for the songs under them, which a
 * wrapping block of chips would push off the screen. The languages carry their mark the way they do under a song in
 * the lists, since neither row has a section title to name it.
 *
 * They are the first item of the list rather than pinned under the search field: with the keyboard up on a small
 * phone, the header, the field and two rows of chips held still left the list itself no room at all, and a chip is
 * picked once where the field is typed into throughout. A library with nothing to filter by gets neither row.
 *
 * The chips selected when the search or the sorting last changed lead each row, in its sorting order, and a divider
 * separates them from the rest. A chip tapped since stays where it is - a sideways row has no room for a copy - and
 * joins them at the next change, as one deselected there stays among them until then ([ChecklistOrder.ordered]). Only
 * that change sends the row back to its start; each row starts with the toggle that switches its order
 * ([SortableChipRow]).
 */
@Composable
private fun PickerFilters(
    modifier: Modifier = Modifier,
    filters: PickerFilterOptions,
    selectedTags: Set<String>,
    selectedLanguages: Set<String>,
    tagOrder: ChecklistOrder,
    languageOrder: ChecklistOrder,
    refreshKey: Any?,
    tagSortingMode: UserPreferences.LabelSortingMode,
    languageSortingMode: UserPreferences.LabelSortingMode,
    onTagClicked: (String) -> Unit,
    onLanguageClicked: (String) -> Unit,
    onTagSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
    onLanguageSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
) {
    val appLanguageCode = currentLanguage.value.code
    val tags = remember(filters.tags, tagSortingMode) { filters.tags.orderedBy(tagSortingMode) }
    val languages = remember(filters.languages, languageSortingMode, appLanguageCode) {
        filters.languages.orderedBy(languageSortingMode) { code -> languageName(code = code, appLanguageCode = appLanguageCode) ?: code.uppercase() }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        if (filters.tags.isNotEmpty()) {
            SortableChipRow(
                items = tags,
                key = { it.name.lowercase() },
                order = tagOrder,
                refreshKey = refreshKey,
                sortingMode = tagSortingMode,
                onSortingModeSelected = onTagSortingModeSelected,
            ) { tag ->
                val key = tag.name.lowercase()
                CountedFilterChip(
                    label = tag.name,
                    songCount = tag.songCount,
                    isSelected = key in selectedTags,
                    onClick = { onTagClicked(key) },
                    leadingIcon = painterResource(Res.drawable.ic_label),
                )
            }
        }
        if (filters.languages.isNotEmpty()) {
            SortableChipRow(
                items = languages,
                key = { it.code },
                order = languageOrder,
                refreshKey = refreshKey to appLanguageCode,
                sortingMode = languageSortingMode,
                onSortingModeSelected = onLanguageSortingModeSelected,
            ) { language ->
                CountedFilterChip(
                    label = languageLabel(language.code),
                    songCount = language.songCount,
                    isSelected = language.code in selectedLanguages,
                    onClick = { onLanguageClicked(language.code) },
                    leadingIcon = painterResource(Res.drawable.ic_language),
                )
            }
        }
    }
}
