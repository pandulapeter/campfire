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
import androidx.compose.ui.graphics.painter.Painter
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.delete
import com.pandulapeter.campfire.presentation.resources.ic_archive
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_duplicate
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_more
import com.pandulapeter.campfire.presentation.resources.ic_unarchive
import com.pandulapeter.campfire.presentation.resources.setlists_actions
import com.pandulapeter.campfire.presentation.resources.setlists_archive
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate
import com.pandulapeter.campfire.presentation.resources.setlists_export
import com.pandulapeter.campfire.presentation.resources.setlists_rename
import com.pandulapeter.campfire.presentation.resources.setlists_unarchive
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import org.jetbrains.compose.resources.painterResource

/**
 * Everything that can be done to one setlist, behind the overflow button of its [SectionHeader] pill: there are more
 * of these than a pill has room for, and all but one of them lead somewhere else anyway - a dialog, a file picker, or
 * the setlist leaving the list it was tapped in.
 *
 * The whole button is absent in performance mode, which the header decides: it is every way of changing a setlist in
 * one place, so there is nothing here to keep.
 */
@Composable
internal fun SetlistActionsMenu(
    viewModel: CampfireViewModel,
    setlist: Setlist,
) {
    val filePicker = LocalFilePicker.current
    OverflowMenu(
        button = { open ->
            SectionHeaderAction(
                icon = painterResource(Res.drawable.ic_more),
                contentDescription = stringResource(Res.string.setlists_actions),
                onClick = open,
            )
        },
    ) { dismiss ->
        // Each entry closes the menu before it acts, so that it is gone by the time the dialog or the picker it
        // opens is on the screen.
        SetlistActionsMenuItem(
            title = stringResource(Res.string.setlists_rename),
            icon = painterResource(Res.drawable.ic_edit),
            onClick = {
                dismiss()
                viewModel.showDialog(CampfireViewModel.DialogType.RenameSetlist(setlist))
            },
        )
        SetlistActionsMenuItem(
            title = stringResource(Res.string.setlists_duplicate),
            icon = painterResource(Res.drawable.ic_duplicate),
            onClick = {
                dismiss()
                viewModel.showDialog(CampfireViewModel.DialogType.DuplicateSetlist(setlist))
            },
        )
        SetlistActionsMenuItem(
            title = if (setlist.isArchived) stringResource(Res.string.setlists_unarchive) else stringResource(Res.string.setlists_archive),
            icon = painterResource(if (setlist.isArchived) Res.drawable.ic_unarchive else Res.drawable.ic_archive),
            onClick = {
                dismiss()
                viewModel.setSetlistArchived(setlist = setlist, isArchived = !setlist.isArchived)
            },
        )
        SetlistActionsMenuItem(
            title = stringResource(Res.string.setlists_export),
            icon = painterResource(Res.drawable.ic_export),
            onClick = {
                dismiss()
                viewModel.exportSetlist(filePicker, setlist.fileName)
            },
        )
        SetlistActionsMenuItem(
            title = stringResource(Res.string.delete),
            icon = painterResource(Res.drawable.ic_delete),
            onClick = {
                dismiss()
                viewModel.showDialog(CampfireViewModel.DialogType.DeleteSetlist(setlist))
            },
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
