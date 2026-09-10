/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.delete
import com.pandulapeter.campfire.presentation.resources.edit
import com.pandulapeter.campfire.presentation.resources.export
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_more
import com.pandulapeter.campfire.presentation.resources.ic_playlist_add
import com.pandulapeter.campfire.presentation.resources.ic_share
import com.pandulapeter.campfire.presentation.resources.share
import com.pandulapeter.campfire.presentation.resources.song_details_add_to_setlist
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import org.jetbrains.compose.resources.painterResource

/**
 * Everything that can be done to one song, handed to [item] one entry at a time so that the same list can be a
 * dropdown menu where there is a pointer to open one with and a bottom sheet where there is not.
 *
 * [item] receives the action rather than performing it, so that each renderer can close itself first: a menu hides
 * before the dialog it opens appears, a sheet animates away first.
 *
 * @param setlistFileName The setlist the song is being shown in, null when it is being shown from the library.
 * @param shouldIncludeAddToSetlist False where the screen already offers it, which the song details app bar does.
 */
@Composable
internal fun SongActions(
    viewModel: CampfireViewModel,
    song: Song,
    setlistFileName: String?,
    shouldIncludeAddToSetlist: Boolean = true,
    item: @Composable (title: String, icon: Painter, isEnabled: Boolean, onClick: () -> Unit) -> Unit
) {
    val filePicker = LocalFilePicker.current
    item(stringResource(Res.string.edit), painterResource(Res.drawable.ic_edit), true) {
        viewModel.openEditor(song.fileName)
    }
    if (shouldIncludeAddToSetlist) {
        item(
            stringResource(Res.string.song_details_add_to_setlist),
            painterResource(Res.drawable.ic_playlist_add),
            true
        ) {
            viewModel.showDialog(
                CampfireViewModel.DialogType.SetlistPicker(songFileName = song.fileName, currentSetlistFileName = setlistFileName)
            )
        }
    }
    item(stringResource(Res.string.export), painterResource(Res.drawable.ic_export), true) {
        viewModel.exportSong(filePicker, song.fileName)
    }
    // Only where sending a file is a different thing from saving one, which on desktop and the web it is not.
    if (filePicker.canShare) {
        item(stringResource(Res.string.share), painterResource(Res.drawable.ic_share), true) {
            viewModel.shareSong(filePicker, song.fileName)
        }
    }
    item(stringResource(Res.string.delete), painterResource(Res.drawable.ic_delete), true) {
        viewModel.showDialog(CampfireViewModel.DialogType.DeleteSong(song))
    }
}

/**
 * The overflow button of a song row and the menu it opens. Touch platforms get the same actions from a long press
 * instead, see [CampfireViewModel.DialogType.SongActions].
 */
@Composable
internal fun SongActionsMenu(
    viewModel: CampfireViewModel,
    song: Song,
    setlistFileName: String?,
    shouldIncludeAddToSetlist: Boolean = true
) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isExpanded = true }) {
            Icon(
                painter = painterResource(Res.drawable.ic_more),
                contentDescription = stringResource(Res.string.songs_actions)
            )
        }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            SongActions(
                viewModel = viewModel,
                song = song,
                setlistFileName = setlistFileName,
                shouldIncludeAddToSetlist = shouldIncludeAddToSetlist
            ) { title, icon, isEnabled, onClick ->
                DropdownMenuItem(
                    text = { Text(title) },
                    leadingIcon = { Icon(painter = icon, contentDescription = null) },
                    enabled = isEnabled,
                    onClick = {
                        isExpanded = false
                        onClick()
                    }
                )
            }
        }
    }
}
