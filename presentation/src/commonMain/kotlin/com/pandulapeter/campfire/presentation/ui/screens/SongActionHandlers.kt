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
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.SongActionHandler
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType

/** The [SongActionHandler] of a screen, which hands every song menu on it to [viewModel]. */
@Composable
internal fun rememberSongActionHandler(viewModel: CampfireViewModel): SongActionHandler = remember(viewModel) {
    object : SongActionHandler {
        override fun edit(song: Song) {
            viewModel.openEditor(song.fileName)
        }

        override fun updateFileName(song: Song) {
            viewModel.updateSongFileName(song)
        }

        override fun export(song: Song, setlistFileName: String?) {
            viewModel.showDialog(DialogType.Export(song = song, songSetlistFileName = setlistFileName))
        }

        override fun delete(song: Song) {
            viewModel.showDialog(DialogType.DeleteSong(song))
        }

        override fun chooseSetlists(song: Song, setlistFileName: String?) {
            viewModel.showDialog(DialogType.SetlistPicker(song = song, setlistFileName = setlistFileName))
        }
    }
}
