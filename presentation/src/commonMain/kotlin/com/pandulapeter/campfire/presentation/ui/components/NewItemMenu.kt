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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.import_files
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import org.jetbrains.compose.resources.painterResource

/**
 * The "New song" and "New setlist" buttons of the two list screens, which ask how the new thing is to arrive before
 * anything else is asked: made here, which goes on to the dialog that names it, or brought in as files, which goes
 * straight to the file picker. The two share nothing past that choice, so neither is offered from inside the other:
 * the names typed into those dialogs mean nothing to an import, which names every song by its own header and every
 * setlist by its own title. The import is the same one on both screens, since a picked archive holds songs and
 * setlists alike and is imported whole wherever it was picked from.
 *
 * The entries are the first two buttons of the screen's own empty state, in the same order and under the same labels,
 * since they are the same two ways of filling the list - and the menu leaves the bar while that state is up, see
 * [allowsNewItemMenu].
 *
 * @param contentDescription What the button is called, since it is an icon.
 * @param createLabel The entry that makes the new thing here rather than importing it.
 * @param onCreate Opens the dialog of [createLabel], called once the menu is closed.
 */
@Composable
internal fun NewItemMenu(
    viewModel: CampfireViewModel,
    contentDescription: String,
    createLabel: String,
    onCreate: () -> Unit,
) {
    val filePicker = LocalFilePicker.current
    OverflowMenu(
        button = { open ->
            IconButton(onClick = open) {
                Icon(
                    painter = painterResource(Res.drawable.ic_add),
                    contentDescription = contentDescription,
                )
            }
        },
    ) { dismiss ->
        DropdownMenuItem(
            text = { Text(createLabel) },
            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_edit), contentDescription = null) },
            onClick = {
                dismiss()
                onCreate()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.import_files)) },
            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_import), contentDescription = null) },
            onClick = {
                dismiss()
                viewModel.importFiles(filePicker)
            },
        )
    }
}
