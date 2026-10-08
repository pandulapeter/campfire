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
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.SetlistActionHandler
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType

/** The [SetlistActionHandler] of a screen, which hands every setlist menu on it to [viewModel]. */
@Composable
internal fun rememberSetlistActionHandler(viewModel: CampfireViewModel): SetlistActionHandler = remember(viewModel) {
    object : SetlistActionHandler {
        override fun edit(setlist: Setlist) {
            viewModel.showDialog(DialogType.EditSetlist(setlist))
        }

        override fun chooseSongs(setlist: Setlist) {
            viewModel.showDialog(DialogType.SongPicker(setlist))
        }

        override fun duplicate(setlist: Setlist) {
            viewModel.showDialog(DialogType.DuplicateSetlist(setlist))
        }

        override fun setArchived(setlist: Setlist, isArchived: Boolean) {
            viewModel.setSetlistArchived(setlist = setlist, isArchived = isArchived)
        }

        override fun export(setlist: Setlist) {
            viewModel.showDialog(DialogType.Export(setlist = setlist))
        }

        override fun delete(setlist: Setlist) {
            viewModel.showDialog(DialogType.DeleteSetlist(setlist))
        }
    }
}
