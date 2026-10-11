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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.create
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_no_search_results
import com.pandulapeter.campfire.presentation.resources.setlists_search
import com.pandulapeter.campfire.presentation.resources.songs_choose_setlists
import com.pandulapeter.campfire.presentation.resources.songs_selection_count
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.SetlistSortMenu
import com.pandulapeter.campfire.presentation.ui.components.checklistItems
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.rememberChecklistOrder
import com.pandulapeter.campfire.presentation.ui.screens.setlists.selectionBoxState
import org.jetbrains.compose.resources.painterResource

/**
 * The [SetlistPicker] of the songs ticked in the selection mode: every setlist with a box that is ticked where it holds
 * all of them, empty where it holds none and half ticked in between. It only ever adds: a tap on an empty or a half
 * ticked box puts the songs the setlist lacks at its end, in the order the songs screen sorts by, as one write, and a
 * tap on a box the sheet filled takes out exactly what it put in. A song the setlist held already stays whatever is
 * tapped, since taking a song out of a setlist loses its place and its key there, which is the setlist's own business,
 * one song at a time and after a question.
 *
 * A setlist that held all of the songs as the sheet opened is ticked and disabled, and an archived one is listed only
 * where it holds one of them, disabled. The boxes follow the sheet's own record of what each setlist held and what it
 * added rather than the library, which only catches up once a write has been round tripped through it.
 */
@Composable
internal fun SelectionSetlistsSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.SelectionSetlists,
) {
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val selection = dialog.songFileNames
    var query by rememberSaveable { mutableStateOf("") }
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val heldAtOpening by rememberSaveable(stateSaver = fileNamesBySetlistSaver) {
        mutableStateOf(setlists.associate { setlist -> setlist.fileName to setlist.entries.map { it.songFileName }.filter { it in selection }.toSet() })
    }
    var added by rememberSaveable(stateSaver = fileNamesBySetlistSaver) { mutableStateOf(emptyMap<String, Set<String>>()) }
    fun stateOf(setlistFileName: String) = selectionBoxState(
        selection = selection,
        held = heldAtOpening[setlistFileName].orEmpty(),
        added = added[setlistFileName].orEmpty(),
    )
    val checkedSetlistKeys = setlists.filter { stateOf(it.fileName) != ToggleableState.Off }.mapTo(mutableSetOf()) { it.fileName }
    val refreshKey = userPreferences?.setlistSortingMode to query
    val setlistOrder = rememberChecklistOrder(checkedSetlistKeys, refreshKey)
    val pickableSetlists = remember(setlists, heldAtOpening) {
        setlists.filter { setlist -> !setlist.isArchived || heldAtOpening[setlist.fileName].orEmpty().isNotEmpty() }
    }
    val matches = remember(pickableSetlists, query) {
        val normalizedQuery = viewModel.songRenderer.normalizeForSearch(query)
        pickableSetlists.filter { setlist ->
            normalizedQuery in viewModel.songRenderer.normalizeForSearch(setlist.title) ||
                normalizedQuery in viewModel.songRenderer.normalizeForSearch(setlist.description)
        }
    }
    val isSkippingToNewSetlist = rememberSaveable { !hasListableSetlist(setlists, selection) }
    var isNamingNewSetlist by rememberSaveable { mutableStateOf(isSkippingToNewSetlist) }
    val closeNamingDialog = { if (isSkippingToNewSetlist) viewModel.dismissSheet(dialog) else isNamingNewSetlist = false }
    if (!isSkippingToNewSetlist) {
        CampfireBottomSheet(
            title = stringResource(Res.string.songs_choose_setlists),
            subtitle = pluralStringResource(Res.plurals.songs_selection_count, selection.size, selection.size),
            actions = { SetlistSortMenu(selected = userPreferences?.setlistSortingMode, onSelected = viewModel::setSetlistSortingMode) },
            onDismiss = { viewModel.dismissSheet(dialog) },
        ) { contentPadding ->
            PickerSearchField(
                query = query,
                placeholder = stringResource(Res.string.setlists_search),
                onQueryChange = { query = it },
            )
            PickerList(
                contentPadding = contentPadding,
                refreshKey = refreshKey,
                contents = matches,
                checklistLayout = remember(matches, setlistOrder) { setlistOrder.layout(matches) { it.fileName } },
                noResultsText = if (matches.isEmpty() && query.isNotBlank()) stringResource(Res.string.setlists_no_search_results) else null,
            ) { listState ->
                checklistItems(
                    items = matches,
                    order = setlistOrder,
                    key = { it.fileName },
                    listState = listState,
                ) { setlist ->
                    val held = heldAtOpening[setlist.fileName].orEmpty()
                    val addedHere = added[setlist.fileName].orEmpty()
                    CheckboxListItem(
                        modifier = listItemAnimation(listState),
                        title = setlist.title,
                        state = stateOf(setlist.fileName),
                        isEnabled = !setlist.isArchived && held.size < selection.size,
                        onClick = {
                            if (addedHere.isEmpty()) {
                                viewModel.addSongsToSetlist(songFileNames = selection, setlistFileName = setlist.fileName)
                                added = added + (setlist.fileName to selection.filterNot { it in held }.toSet())
                            } else {
                                viewModel.takeSongsOutOfSetlist(songFileNames = addedHere, setlistFileName = setlist.fileName)
                                added = added - setlist.fileName
                            }
                        },
                    )
                }
                item(key = "new_setlist") {
                    ActionListItem(
                        modifier = listItemAnimation(listState),
                        title = stringResource(Res.string.setlists_new_setlist),
                        icon = painterResource(Res.drawable.ic_add),
                        onClick = { isNamingNewSetlist = true },
                    )
                }
            }
        }
    }
    if (isNamingNewSetlist) {
        SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_new_setlist),
            initialTitle = query.trim(),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = closeNamingDialog,
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.createSetlistWithSongs(
                    title = setlistTitle,
                    description = description,
                    date = date,
                    isCountdownShown = isCountdownShown,
                    songFileNames = selection,
                )
            },
        )
    }
}

/**
 * What the sheet holds about each setlist, by its file name, as a Bundle can hold it: one flat list of each setlist's
 * name, how many songs follow it and those songs.
 */
private val fileNamesBySetlistSaver = Saver<Map<String, Set<String>>, ArrayList<String>>(
    save = { map ->
        map.flatMapTo(ArrayList()) { (setlist, fileNames) -> listOf(setlist, fileNames.size.toString()) + fileNames }
    },
    restore = { saved ->
        buildMap {
            var index = 0
            while (index < saved.size) {
                val count = saved[index + 1].toInt()
                put(saved[index], saved.subList(index + 2, index + 2 + count).toSet())
                index += 2 + count
            }
        }
    },
)
