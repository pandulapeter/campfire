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
import com.pandulapeter.campfire.presentation.resources.songs_delete_song
import com.pandulapeter.campfire.presentation.resources.songs_delete_song_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_delete_song_confirmation_shared
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.sync.isShared

/**
 * Deleting a song shared as a link stops sharing it (`DeleteSongUseCase`), so the question says so while it can still
 * be answered no. A song that is only inside a shared setlist needs no word: the setlist's copy simply loses it.
 */
@Composable
internal fun DeleteSongConfirmation(
    viewModel: CampfireViewModel,
    dialog: DialogType.DeleteSong,
) {
    val shares by viewModel.shareLinkController.shares.collectAsStateWithLifecycle()
    ConfirmationDialog(
        title = stringResource(Res.string.songs_delete_song),
        text = textResource(
            if (shares.isShared(SharedLinkKind.SONG, dialog.song.fileName)) {
                Res.string.songs_delete_song_confirmation_shared
            } else {
                Res.string.songs_delete_song_confirmation
            },
            dialog.song.title,
        ),
        confirmLabel = stringResource(Res.string.delete),
        onDismiss = viewModel.dialogHost::dismissDialog,
        onConfirm = {
            viewModel.songFilesController.deleteSong(dialog.song.fileName)
            viewModel.dialogHost.dismissDialog()
        },
    )
}
