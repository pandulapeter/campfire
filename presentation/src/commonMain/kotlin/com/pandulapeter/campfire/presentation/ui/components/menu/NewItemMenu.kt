/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.menu

import com.pandulapeter.campfire.presentation.ui.components.list.allowsNewItemMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_content_paste
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.import_files
import com.pandulapeter.campfire.presentation.resources.songs_paste_from_clipboard
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
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
 * The New song menu has a third way, a chord sheet pasted from the clipboard, which is made here like the first and
 * read like the second: it goes to the New song dialog filled in from the text where the text is one song, and is the
 * import that text would be as a file otherwise. The New setlist menu has no such entry.
 *
 * The entries are the first buttons of the screen's own empty state, in the same order and under the same labels,
 * since they are the same ways of filling the list - and the menu leaves the bar while that state is up, see
 * [allowsNewItemMenu].
 *
 * @param contentDescription What the button is called, since it is an icon.
 * @param createLabel The entry that makes the new thing here rather than importing it.
 * @param onItemSelected Called before either menu action starts.
 * @param onCreate Opens the dialog of [createLabel], called once the menu is closed.
 * @param onImport Imports what is picked with the file picker it is handed, called once the menu is closed.
 * @param onPaste Makes a new song of the clipboard's text, called once the menu is closed; null for no such entry.
 */
@Composable
internal fun NewItemMenu(
    contentDescription: String,
    createLabel: String,
    onCreate: () -> Unit,
    onImport: (FilePicker) -> Unit,
    onPaste: (() -> Unit)? = null,
    onItemSelected: () -> Unit = {},
    isEnabled: Boolean = true,
) {
    val filePicker = LocalFilePicker.current
    OverflowMenu(
        button = { open ->
            // Filled in the accent color, unlike the bar's other actions: it is the way to fill the list, and a plain
            // icon among search, sort, and filter is the one that gets overlooked.
            FilledIconButton(modifier = Modifier.size(NEW_ITEM_BUTTON_SIZE), onClick = open, enabled = isEnabled) {
                Icon(
                    painter = painterResource(Res.drawable.ic_add),
                    contentDescription = contentDescription,
                )
            }
        },
    ) { select ->
        DropdownMenuItem(
            text = { Text(createLabel) },
            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_edit), contentDescription = null) },
            onClick = {
                select {
                    onItemSelected()
                    onCreate()
                }
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.import_files)) },
            leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_import), contentDescription = null) },
            onClick = {
                select {
                    onItemSelected()
                    onImport(filePicker)
                }
            },
        )
        onPaste?.let { paste ->
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.songs_paste_from_clipboard)) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_content_paste), contentDescription = null) },
                onClick = {
                    select {
                        onItemSelected()
                        paste()
                    }
                },
            )
        }
    }
}

/** The New button's circle, as tall as the pill beside it, so that the two read as one row of the bar's controls. */
internal val NEW_ITEM_BUTTON_SIZE = 48.dp
