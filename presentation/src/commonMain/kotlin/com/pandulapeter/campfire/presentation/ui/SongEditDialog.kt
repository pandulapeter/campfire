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
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cover_art_search_remove
import com.pandulapeter.campfire.presentation.resources.song_details_remove_cover_art_confirmation
import com.pandulapeter.campfire.presentation.ui.dialogs.ConfirmationDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.SongLinksDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.SongMetadataDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.SongPlayingDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.labels.SongLanguagesDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.labels.SongTagsDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.lookup.CoverArtSearchSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.lookup.SongDetailsLookupSheet

/** One open dialog of [CampfireDialogs] about one song's file ([DialogType.SongEdit]), whichever place in the stack it has. */
@Composable
internal fun SongEditDialog(
    viewModel: CampfireViewModel,
    dialog: DialogType.SongEdit,
) {
    when (dialog) {
        is DialogType.SongTags -> SongTagsDialog(
            libraryTags = viewModel.libraryState.tags,
            preferencesController = viewModel.preferencesController,
            songRenderer = viewModel.songRenderer,
            songMetadataEditing = viewModel.songMetadataEditing,
            onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
            dialog = dialog,
        )

        is DialogType.SongMetadata -> SongMetadataDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SongDetailsLookup -> SongDetailsLookupSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SongLinks -> SongLinksDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.SongLanguages -> SongLanguagesDialog(
            libraryLanguages = viewModel.libraryState.languages,
            preferencesController = viewModel.preferencesController,
            songRenderer = viewModel.songRenderer,
            songMetadataEditing = viewModel.songMetadataEditing,
            onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
            dialog = dialog,
        )

        is DialogType.SongPlaying -> SongPlayingDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.CoverArtSearch -> CoverArtSearchSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        is DialogType.RemoveSongCoverArt -> ConfirmationDialog(
            title = stringResource(Res.string.cover_art_search_remove),
            text = textResource(Res.string.song_details_remove_cover_art_confirmation, dialog.song.title),
            confirmLabel = stringResource(Res.string.cover_art_search_remove),
            // Not removing the cover is not being done with it: the cover sheet the bin was tapped in is still under it.
            onDismiss = { viewModel.dialogHost.dismissDialog() },
            onConfirm = {
                viewModel.songMetadataEditing.setSongCoverArt(target = dialog.target, url = null)
                // The cover sheet under the question goes with it, sliding away: there is nothing left for it to change.
                viewModel.dialogHost.dismissDialogAndSheetUnder()
            },
        )
    }
}
