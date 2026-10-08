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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.create
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_no_search_results
import com.pandulapeter.campfire.presentation.resources.setlists_search
import com.pandulapeter.campfire.presentation.resources.songs_choose_setlists
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.checklistItems
import com.pandulapeter.campfire.presentation.ui.components.rememberChecklistOrder
import com.pandulapeter.campfire.presentation.ui.components.SetlistSortMenu
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import org.jetbrains.compose.resources.painterResource

/**
 * The setlists one song can be put into, and the way to make a new one. Naming that new setlist happens in a dialog
 * on top of the sheet rather than instead of it: the setlist is only being created so that this song can go into it,
 * so the sheet staying where it is, with a ticked row appearing in it, is what says that it worked.
 *
 * A library with no setlist the song could be put in - none at all, or only archived ones it is not in - skips the
 * sheet and asks for the name of a new one straight away, since a sheet offering nothing to tick is one tap in the way
 * of the only thing that can be done there. Whether that is what happened is decided once, as the dialog opens
 * ([isSkippingToNewSetlist], through [hasListableSetlist]) rather than read from the list as it stands: creating the
 * setlist fills the list, and the sheet must not slide in behind a dialog that is on its way out. It is also what the
 * naming dialog is closed by, since there is nothing behind it to return to.
 */
@Composable
internal fun SetlistPicker(
    viewModel: CampfireViewModel,
    dialog: DialogType.SetlistPicker,
) {
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val checkedSetlistKeys = setlists.filter { setlist -> setlist.entries.any { it.songFileName == dialog.song.fileName } }
        .mapTo(mutableSetOf()) { it.fileName }
    val refreshKey = userPreferences?.setlistSortingMode to query
    val setlistOrder = rememberChecklistOrder(checkedSetlistKeys, refreshKey)
    // Archived setlists are offered only while checked or retained after an uncheck. A refresh drops the latter,
    // just as it returns the other unchecked rows to the regular sorting order.
    val pickableSetlists = remember(setlists, setlistOrder.heldKeys) {
        setlists.filter { setlist -> !setlist.isArchived || setlist.fileName in setlistOrder.heldKeys }
    }
    // Answered by the title or the description, the way the setlists screen's own search answers, but not by the
    // songs inside: the song this sheet is about is the only one that matters here.
    val matches = remember(pickableSetlists, query) {
        val normalizedQuery = viewModel.normalizeForSearch(query)
        pickableSetlists.filter { setlist ->
            normalizedQuery in viewModel.normalizeForSearch(setlist.title) || normalizedQuery in viewModel.normalizeForSearch(setlist.description)
        }
    }
    val isSkippingToNewSetlist = rememberSaveable { !hasListableSetlist(setlists, dialog.song.fileName) }
    var isNamingNewSetlist by rememberSaveable { mutableStateOf(isSkippingToNewSetlist) }
    val closeNamingDialog = { if (isSkippingToNewSetlist) viewModel.dismissSheet(dialog) else isNamingNewSetlist = false }
    if (!isSkippingToNewSetlist) {
        CampfireBottomSheet(
            title = stringResource(Res.string.songs_choose_setlists),
            subtitle = songLabel(dialog.song),
            actions = { SetlistSortMenu(viewModel = viewModel) },
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
                    CheckboxListItem(
                        modifier = listItemAnimation(listState),
                        title = setlist.title,
                        isChecked = setlist.entries.any { it.songFileName == dialog.song.fileName },
                        isEnabled = !setlist.isArchived && setlist.fileName != dialog.setlistFileName,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                viewModel.addSongToSetlist(songFileName = dialog.song.fileName, setlistFileName = setlist.fileName)
                            } else {
                                viewModel.removeSongFromSetlist(songFileName = dialog.song.fileName, setlistFileName = setlist.fileName)
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
            // A search that found nothing is most likely the name of the setlist that is missing, and it opens
            // selected, so typing something else instead costs nothing.
            initialTitle = query.trim(),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = closeNamingDialog,
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.createSetlistWithSong(
                    title = setlistTitle,
                    description = description,
                    date = date,
                    isCountdownShown = isCountdownShown,
                    songFileName = dialog.song.fileName,
                )
            },
        )
    }
}
