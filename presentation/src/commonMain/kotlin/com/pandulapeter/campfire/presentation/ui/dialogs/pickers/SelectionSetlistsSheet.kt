/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.pickers

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.SetlistDetailsDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
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
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.checklist.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.menu.SetlistSortMenu
import com.pandulapeter.campfire.presentation.ui.components.checklist.checklistItems
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.checklist.rememberChecklistOrder
import com.pandulapeter.campfire.presentation.ui.components.SearchField
import com.pandulapeter.campfire.presentation.ui.dialogs.pickers.heldOf
import com.pandulapeter.campfire.presentation.ui.dialogs.pickers.selectionBoxState
import com.pandulapeter.campfire.presentation.ui.dialogs.pickers.withHeldRecorded
import org.jetbrains.compose.resources.painterResource

/**
 * The [SetlistPicker] of the songs ticked in the selection mode: every setlist with a box that is ticked where it holds
 * all of them, empty where it holds none and half ticked in between. It only ever adds: a tap on an empty or a half
 * ticked box puts the songs the setlist lacks at its end, in the order the songs screen sorts by, as one write, and a
 * tap on a box the sheet filled takes out exactly what it put in. A song the setlist held already stays whatever is
 * tapped, since taking a song out of a setlist loses its place and its key there, which is the setlist's own business,
 * one song at a time and after a question.
 *
 * A setlist that holds all of the songs, and that the sheet has not added to, is ticked and disabled, and an archived
 * one is listed only where it holds one of them, disabled. A setlist the sheet has not been tapped on yet follows the
 * library; from its first tap on, its box follows the sheet's own record of what it held then and what the sheet added
 * since rather than the library, which only catches up once a write has been round tripped through it.
 */
@Composable
internal fun SelectionSetlistsSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.SelectionSetlists,
) {
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val selection = dialog.songFileNames
    val selectionSet = remember(selection) { selection.toHashSet() }
    var query by rememberSaveable { mutableStateOf("") }
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    var heldWhenTapped by rememberSaveable(stateSaver = fileNamesBySetlistSaver) { mutableStateOf(emptyMap<String, Set<String>>()) }
    var added by rememberSaveable(stateSaver = fileNamesBySetlistSaver) { mutableStateOf(emptyMap<String, Set<String>>()) }
    fun stateOf(setlist: Setlist) = selectionBoxState(
        selection = selectionSet,
        held = heldOf(heldWhenTapped, setlist, selectionSet),
        added = added[setlist.fileName].orEmpty(),
    )
    val boxStates = remember(setlists, selectionSet, heldWhenTapped, added) {
        setlists.associate { it.fileName to stateOf(it) }
    }
    val checkedSetlistKeys = remember(boxStates) { boxStates.filterValues { it != ToggleableState.Off }.keys }
    val refreshKey = userPreferences?.setlistSortingMode to query
    val setlistOrder = rememberChecklistOrder(checkedSetlistKeys, refreshKey)
    val pickableSetlists = remember(setlists, heldWhenTapped) {
        setlists.filter { setlist -> !setlist.isArchived || heldOf(heldWhenTapped, setlist, selectionSet).isNotEmpty() }
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
            SearchField(
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
                    val held = heldOf(heldWhenTapped, setlist, selectionSet)
                    val addedHere = added[setlist.fileName].orEmpty()
                    CheckboxListItem(
                        modifier = listItemAnimation(listState),
                        title = setlist.title,
                        state = boxStates[setlist.fileName] ?: stateOf(setlist),
                        isEnabled = !setlist.isArchived && held.size < selection.size,
                        onClick = {
                            heldWhenTapped = withHeldRecorded(heldWhenTapped, setlist, selectionSet)
                            val heldHere = heldWhenTapped.getValue(setlist.fileName)
                            if (addedHere.isEmpty()) {
                                viewModel.addSongsToSetlist(songFileNames = selection, setlistFileName = setlist.fileName)
                                added = added + (setlist.fileName to selection.filterNot { it in heldHere }.toSet())
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
