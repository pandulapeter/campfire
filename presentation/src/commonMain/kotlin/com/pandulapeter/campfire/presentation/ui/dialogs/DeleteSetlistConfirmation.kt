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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.delete
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist_confirmation
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist_confirmation_shared
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.sync.isShared

/** Deleting a setlist shared as a link stops sharing it (`DeleteSetlistUseCase`), so the question says so. */
@Composable
internal fun DeleteSetlistConfirmation(
    viewModel: CampfireViewModel,
    dialog: DialogType.DeleteSetlist,
) {
    val shares by viewModel.shareLinkController.shares.collectAsStateWithLifecycle()
    ConfirmationDialog(
        title = stringResource(Res.string.setlists_delete_setlist),
        text = textResource(
            if (shares.isShared(SharedLinkKind.SETLIST, dialog.setlist.fileName)) {
                Res.string.setlists_delete_setlist_confirmation_shared
            } else {
                Res.string.setlists_delete_setlist_confirmation
            },
            dialog.setlist.title,
        ),
        confirmLabel = stringResource(Res.string.delete),
        onDismiss = viewModel.dialogHost::dismissDialog,
        onConfirm = {
            viewModel.setlistsController.deleteSetlist(dialog.setlist.fileName)
            viewModel.dialogHost.dismissDialog()
        },
    )
}
