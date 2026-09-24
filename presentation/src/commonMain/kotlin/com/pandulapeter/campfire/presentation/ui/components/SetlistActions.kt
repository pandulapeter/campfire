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

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
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
import com.pandulapeter.campfire.presentation.resources.setlists_edit_title_and_description
import com.pandulapeter.campfire.presentation.resources.setlists_export
import com.pandulapeter.campfire.presentation.resources.setlists_song_assignments
import com.pandulapeter.campfire.presentation.resources.setlists_unarchive
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import org.jetbrains.compose.resources.painterResource

/**
 * Everything that can be done to one setlist, behind the overflow button of its [SectionHeader]: there are more
 * of these than a row has room for, and all but one of them lead somewhere else anyway - a sheet, a dialog, a file
 * picker, or the setlist leaving the list it was tapped in. They start the way a song's menu does, with "Edit" and
 * then the assignments, so that the two menus read alike. "Edit" is where a setlist is renamed, and also the one
 * place its description is written, since the two are the whole of what the user gets to say about it.
 *
 * The whole button is absent in performance mode, which the header decides: it is every way of changing a setlist in
 * one place, so there is nothing here to keep.
 */
@Composable
internal fun SetlistActionsMenu(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    setlist: Setlist,
) {
    val filePicker = LocalFilePicker.current
    ActionsMenu(
        modifier = modifier,
        contentDescription = stringResource(Res.string.setlists_actions),
    ) { select ->
        // Each entry acts through `select`, which closes the menu before it acts - so that it is gone by the time the
        // dialog or the picker it opens is on the screen - and only once.
        SetlistActionsMenuItem(
            title = stringResource(Res.string.setlists_edit_title_and_description),
            icon = painterResource(Res.drawable.ic_edit),
            onClick = { select { viewModel.showDialog(CampfireViewModel.DialogType.EditSetlist(setlist)) } },
        )
        SetlistActionsMenuItem(
            title = stringResource(Res.string.setlists_song_assignments),
            icon = painterResource(Res.drawable.ic_songs),
            onClick = { select { viewModel.showDialog(CampfireViewModel.DialogType.SongPicker(setlist)) } },
        )
        SetlistActionsMenuItem(
            title = stringResource(Res.string.setlists_duplicate_setlist),
            icon = painterResource(Res.drawable.ic_duplicate),
            onClick = { select { viewModel.showDialog(CampfireViewModel.DialogType.DuplicateSetlist(setlist)) } },
        )
        SetlistActionsMenuItem(
            title = if (setlist.isArchived) stringResource(Res.string.setlists_unarchive) else stringResource(Res.string.setlists_archive),
            icon = painterResource(if (setlist.isArchived) Res.drawable.ic_unarchive else Res.drawable.ic_archive),
            onClick = { select { viewModel.setSetlistArchived(setlist = setlist, isArchived = !setlist.isArchived) } },
        )
        SetlistActionsMenuItem(
            title = stringResource(Res.string.setlists_export),
            icon = painterResource(Res.drawable.ic_export),
            onClick = { select { viewModel.exportSetlist(filePicker, setlist.fileName) } },
        )
        SetlistActionsMenuItem(
            title = stringResource(Res.string.setlists_delete_setlist),
            icon = painterResource(Res.drawable.ic_delete),
            onClick = { select { viewModel.showDialog(CampfireViewModel.DialogType.DeleteSetlist(setlist)) } },
        )
    }
}

@Composable
private fun SetlistActionsMenuItem(
    title: String,
    icon: Painter,
    onClick: () -> Unit,
) = DropdownMenuItem(
    text = { Text(title) },
    leadingIcon = {
        Icon(
            painter = icon,
            contentDescription = null,
        )
    },
    onClick = onClick,
)
