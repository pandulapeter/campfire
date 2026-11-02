/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.forms

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.setlists_edit_details
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import kotlinx.datetime.LocalDate

/** The details of a setlist ([SetlistDetailsDialog]) opened on what it has, saved only once one of them changed. */
@Composable
internal fun EditSetlistDialog(
    editSetlist: (offered: Setlist, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) -> Unit,
    onDismiss: () -> Unit,
    dialog: DialogType.EditSetlist,
) = SetlistDetailsDialog(
    title = stringResource(Res.string.setlists_edit_details),
    subtitle = dialog.setlist.title,
    isTitleFocused = false,
    requiresChanges = true,
    initialTitle = dialog.setlist.title,
    initialDescription = dialog.setlist.description,
    initialDate = dialog.setlist.date,
    initialIsCountdownShown = dialog.setlist.isCountdownShown,
    confirmLabel = stringResource(Res.string.save),
    onDismiss = onDismiss,
    onConfirm = { setlistTitle, description, date, isCountdownShown ->
        editSetlist(dialog.setlist, setlistTitle, description, date, isCountdownShown)
    },
)
