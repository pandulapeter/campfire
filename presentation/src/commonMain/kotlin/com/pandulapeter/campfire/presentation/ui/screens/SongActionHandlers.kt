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
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.components.actions.SongActionHandler
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.state.editor.EditorSession
import com.pandulapeter.campfire.presentation.ui.state.songs.SongFilesController

/** The [SongActionHandler] of a screen, which hands every song menu on it to the holders that answer it. */
@Composable
internal fun rememberSongActionHandler(
    editorSession: EditorSession,
    songFilesController: SongFilesController,
    dialogHost: DialogHost,
): SongActionHandler = remember(editorSession, songFilesController, dialogHost) {
    object : SongActionHandler {
        override fun edit(song: Song) {
            editorSession.openEditor(song.fileName)
        }

        override fun updateFileName(song: Song) {
            songFilesController.updateSongFileName(song)
        }

        override fun duplicate(song: Song) {
            songFilesController.showDuplicateSongDialog(song)
        }

        override fun export(song: Song, setlistFileName: String?) {
            dialogHost.showDialog(DialogType.Export(song = song, songSetlistFileName = setlistFileName))
        }

        override fun delete(song: Song) {
            dialogHost.showDialog(DialogType.DeleteSong(song))
        }

        override fun chooseSetlists(song: Song, setlistFileName: String?) {
            dialogHost.showDialog(DialogType.SetlistPicker(song = song, setlistFileName = setlistFileName))
        }
    }
}
