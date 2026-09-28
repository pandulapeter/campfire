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
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_unarchive
import com.pandulapeter.campfire.presentation.resources.setlists_actions
import com.pandulapeter.campfire.presentation.resources.setlists_archive
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_edit_details
import com.pandulapeter.campfire.presentation.resources.setlists_export
import com.pandulapeter.campfire.presentation.resources.setlists_song_assignments
import com.pandulapeter.campfire.presentation.resources.setlists_unarchive
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import org.jetbrains.compose.resources.painterResource

/**
 * Everything that can be done to one setlist, at the end of its [SectionHeader]: "Edit" and "Song assignments" as
 * buttons where the header has the room for them, and the rest behind its overflow button ([ActionsMenu]) whatever the
 * room - duplicating, archiving and exporting are rarely wanted, and deleting is not to be ended up in by accident. They
 * start the way a song's actions do, with "Edit" and then the assignments, so that the two read alike. "Edit" is where
 * a setlist is renamed, and also the one place its description is written, since the two are the whole of what the
 * user gets to say about it.
 *
 * The whole of it is absent in performance mode, which the header decides: it is every way of changing a setlist in
 * one place, so there is nothing here to keep.
 *
 * @param modifier Put on the whole row of buttons, whose largest width is the room the actions may take.
 * @param buttonModifier Put on every button.
 * @param isDecorative Draws the icons alone, for the header's copy that is being pushed away.
 */
@Composable
internal fun SetlistActions(
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    setlist: Setlist,
    isDecorative: Boolean = false,
) {
    val filePicker = LocalFilePicker.current
    ActionsMenu(
        modifier = modifier,
        buttonModifier = buttonModifier,
        contentDescription = stringResource(Res.string.setlists_actions),
        isDecorative = isDecorative,
        items = listOf(
            ActionsMenuItem(
                title = stringResource(Res.string.setlists_edit_details),
                icon = painterResource(Res.drawable.ic_edit),
                onClick = { viewModel.showDialog(CampfireViewModel.DialogType.EditSetlist(setlist)) },
            ),
            ActionsMenuItem(
                title = stringResource(Res.string.setlists_song_assignments),
                icon = painterResource(Res.drawable.ic_songs),
                onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongPicker(setlist)) },
            ),
            ActionsMenuItem(
                title = stringResource(Res.string.setlists_duplicate_setlist),
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
                onClick = { viewModel.exportSetlist(filePicker, setlist.fileName) },
            ),
            ActionsMenuItem(
                title = stringResource(Res.string.setlists_delete_setlist),
                icon = painterResource(Res.drawable.ic_delete),
                isAlwaysInMenu = true,
                onClick = { viewModel.showDialog(CampfireViewModel.DialogType.DeleteSetlist(setlist)) },
            ),
        ),
    )
}
