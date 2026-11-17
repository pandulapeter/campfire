/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_editor_revert
import com.pandulapeter.campfire.presentation.resources.song_editor_revert_confirmation
import com.pandulapeter.campfire.presentation.ui.dialogs.ConfirmationDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.PictureLinkSheet

/** One open dialog of [CampfireDialogs] that only the editor opens, about the text it holds. */
@Composable
internal fun EditorDialog(
    viewModel: CampfireViewModel,
    dialog: DialogType,
) {
    when (dialog) {
        DialogType.RevertChanges -> ConfirmationDialog(
            title = stringResource(Res.string.song_editor_revert),
            text = stringResource(Res.string.song_editor_revert_confirmation),
            confirmLabel = stringResource(Res.string.song_editor_revert),
            onDismiss = viewModel.dialogHost::dismissDialog,
            onConfirm = viewModel.editorSession::revertEditorChanges,
        )

        is DialogType.PictureLink -> PictureLinkSheet(
            onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
            onDownload = { url -> viewModel.editorSession.images.downloadImage(fileName = dialog.editorFileName, url = url) },
        )

        else -> Unit
    }
}
