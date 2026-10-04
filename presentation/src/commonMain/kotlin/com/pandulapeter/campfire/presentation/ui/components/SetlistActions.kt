/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_archive
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_duplicate
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_reorder_songs
import com.pandulapeter.campfire.presentation.resources.ic_reorder_songs_done
import com.pandulapeter.campfire.presentation.resources.setlists_done_reordering
import com.pandulapeter.campfire.presentation.resources.setlists_reorder
import com.pandulapeter.campfire.presentation.resources.setlists_export
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_unarchive
import com.pandulapeter.campfire.presentation.resources.setlists_actions
import com.pandulapeter.campfire.presentation.resources.setlists_archive
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_edit_details
import com.pandulapeter.campfire.presentation.resources.setlists_song_assignments
import com.pandulapeter.campfire.presentation.resources.setlists_unarchive
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import org.jetbrains.compose.resources.painterResource

/**
 * Everything that can be done to one setlist, at the end of its [SectionHeader]. Song assignments appear as a
 * button when the header has room, otherwise they stay in the overflow menu. Reordering stays in the menu and
 * becomes "Done reordering" while active. Editing, duplicating, archiving, exporting and deleting stay there too.
 *
 * The whole of it is absent in performance mode, which the header decides: it is every way of changing a setlist in
 * one place, so there is nothing here to keep.
 *
 * @param modifier Put on the whole row of buttons, whose largest width is the room the actions may take.
 * @param buttonModifier Put on every button.
 * @param isDecorative Draws the icons alone, for the header's copy that is being pushed away.
 * @param onReorder Toggles reordering mode when this setlist has enough songs to reorder.
 */
@Composable
internal fun SetlistActions(
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    setlist: Setlist,
    isDecorative: Boolean = false,
    onReorder: (() -> Unit)? = null,
    isReordering: Boolean = false,
) {
    ActionsMenu(
        modifier = modifier,
        buttonModifier = buttonModifier,
        contentDescription = stringResource(Res.string.setlists_actions),
        isDecorative = isDecorative,
        items = listOfNotNull(
            ActionsMenuItem(
                title = stringResource(Res.string.setlists_edit_details),
                isVisible = !setlist.isArchived,
                icon = painterResource(Res.drawable.ic_edit),
                isAlwaysInMenu = true,
                onClick = { viewModel.showDialog(CampfireViewModel.DialogType.EditSetlist(setlist)) },
            ),
            ActionsMenuItem(
                title = stringResource(Res.string.setlists_song_assignments),
                isVisible = !setlist.isArchived,
                icon = painterResource(Res.drawable.ic_songs),
                onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongPicker(setlist)) },
            ),
            onReorder?.let {
                ActionsMenuItem(
                    title = stringResource(if (isReordering) Res.string.setlists_done_reordering else Res.string.setlists_reorder),
                    icon = painterResource(if (isReordering) Res.drawable.ic_reorder_songs_done else Res.drawable.ic_reorder_songs),
                    key = "reorder_songs",
                    isAlwaysInMenu = true,
                    isVisible = !setlist.isArchived,
                    animateIconChange = true,
                    onClick = it,
                )
            },
            ActionsMenuItem(
                title = stringResource(Res.string.setlists_duplicate_setlist),
                isVisible = !setlist.isArchived,
                icon = painterResource(Res.drawable.ic_duplicate),
                isAlwaysInMenu = true,
                onClick = { viewModel.showDialog(CampfireViewModel.DialogType.DuplicateSetlist(setlist)) },
            ),
            ActionsMenuItem(
                title = if (setlist.isArchived) stringResource(Res.string.setlists_unarchive) else stringResource(Res.string.setlists_archive),
                icon = painterResource(if (setlist.isArchived) Res.drawable.ic_unarchive else Res.drawable.ic_archive),
                isAlwaysInMenu = true,
                onClick = { viewModel.setSetlistArchived(setlist = setlist, isArchived = !setlist.isArchived) },
            ),
            ActionsMenuItem(
                title = stringResource(Res.string.setlists_export),
                icon = painterResource(Res.drawable.ic_export),
                isAlwaysInMenu = true,
                onClick = { viewModel.showDialog(CampfireViewModel.DialogType.Export(setlist = setlist)) },
            ),
            ActionsMenuItem(
                title = stringResource(Res.string.setlists_delete_setlist),
                isVisible = !setlist.isArchived,
                icon = painterResource(Res.drawable.ic_delete),
                isAlwaysInMenu = true,
                onClick = { viewModel.showDialog(CampfireViewModel.DialogType.DeleteSetlist(setlist)) },
            ),
        ),
    )
}
