/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.presentation.ui.components.actions.SetlistActionHandler
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.state.setlists.SetlistsController

/** The [SetlistActionHandler] of a screen, which hands every setlist menu on it to the holders that answer it. */
@Composable
internal fun rememberSetlistActionHandler(
    dialogHost: DialogHost,
    setlistsController: SetlistsController,
): SetlistActionHandler = remember(dialogHost, setlistsController) {
    object : SetlistActionHandler {
        override fun edit(setlist: Setlist) {
            dialogHost.showDialog(DialogType.EditSetlist(setlist))
        }

        override fun chooseSongs(setlist: Setlist) {
            dialogHost.showDialog(DialogType.SongPicker(setlist))
        }

        override fun duplicate(setlist: Setlist) {
            dialogHost.showDialog(DialogType.DuplicateSetlist(setlist))
        }

        override fun setArchived(setlist: Setlist, isArchived: Boolean) {
            setlistsController.setSetlistArchived(setlist = setlist, isArchived = isArchived)
        }

        override fun export(setlist: Setlist) {
            dialogHost.showDialog(DialogType.Export(setlist = setlist))
        }

        override fun delete(setlist: Setlist) {
            dialogHost.showDialog(DialogType.DeleteSetlist(setlist))
        }
    }
}
