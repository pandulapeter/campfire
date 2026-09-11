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

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.pandulapeter.campfire.presentation.resources.ic_rename
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_setlists_outline
import com.pandulapeter.campfire.presentation.resources.ic_share
import com.pandulapeter.campfire.presentation.resources.share
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.songs_update_file_name
import com.pandulapeter.campfire.presentation.resources.songs_setlist_assignments
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
 * @param lockedSetlistFileName The one setlist the assignments sheet may not take the song out of, because the
 *   screen that opened it is showing the song as part of that setlist and would have the ground pulled from under
 *   it. Null on the setlists screen, where a row is the song's membership of the setlist and giving it up is the
 *   whole point, and null in the library, where no setlist is in play.
 * @param shouldIncludeSetlistAssignments False where the screen already offers them, which the song details app bar
 *   does.
 */
@Composable
internal fun SongActions(
    viewModel: CampfireViewModel,
    song: Song,
    lockedSetlistFileName: String?,
    shouldIncludeSetlistAssignments: Boolean = true,
    item: @Composable (title: String, icon: Painter, isEnabled: Boolean, onClick: () -> Unit) -> Unit,
) {
    val filePicker = LocalFilePicker.current
    val songFileNamesInSetlists by viewModel.songFileNamesInSetlists.collectAsStateWithLifecycle()
    item(stringResource(Res.string.edit), painterResource(Res.drawable.ic_edit), true) {
        viewModel.openEditor(song.fileName)
    }
    // One entry for both directions: the sheet it opens is a list of every setlist with a box each, so putting the
    // song into one and taking it out of another are the same gesture there, and a menu that offered them as two
    // separate actions was naming the same sheet twice.
    if (shouldIncludeSetlistAssignments) {
        item(
            stringResource(Res.string.songs_setlist_assignments),
            painterResource(if (song.fileName in songFileNamesInSetlists) Res.drawable.ic_setlists else Res.drawable.ic_setlists_outline),
            true,
        ) {
            viewModel.showDialog(
                CampfireViewModel.DialogType.SetlistPicker(song = song, lockedSetlistFileName = lockedSetlistFileName)
            )
        }
    }
    // Only where it would do something: a file already named after its own metadata, or one with no title to be
    // named after, has nothing to update and the entry would be an offer that never comes to anything.
    if (song.canUpdateFileName) {
        item(stringResource(Res.string.songs_update_file_name), painterResource(Res.drawable.ic_rename), true) {
            viewModel.updateSongFileName(song)
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
 * The overflow button of a list row and the menu it opens, filled by [content] with one [ActionsMenuItem] per
 * action. [content] is handed the way to close the menu, which every entry has to call before the dialog or screen
 * it opens appears.
 *
 * Separate from [SongActionsMenu] because not every row that wants a menu has a song behind it: a setlist entry
 * whose file has gone missing still has the one action of being taken out of the setlist.
 */
@Composable
internal fun ActionsMenu(
    content: @Composable (dismiss: () -> Unit) -> Unit,
) = OverflowMenu(
    button = { open ->
        IconButton(onClick = open) {
            Icon(
                painter = painterResource(Res.drawable.ic_more),
                contentDescription = stringResource(Res.string.songs_actions),
            )
        }
    },
    content = content,
)

/** One entry of an [ActionsMenu]. */
@Composable
internal fun ActionsMenuItem(
    title: String,
    icon: Painter,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) = DropdownMenuItem(
    text = { Text(title) },
    leadingIcon = { Icon(painter = icon, contentDescription = null) },
    enabled = isEnabled,
    onClick = onClick,
)

/**
 * The overflow button of a song row and the menu it opens. Touch platforms get the same actions from a long press
 * instead, see [CampfireViewModel.DialogType.SongActions] - except on the setlists screen, where the long press
 * reorders the row and the button is the only way in.
 */
@Composable
internal fun SongActionsMenu(
    viewModel: CampfireViewModel,
    song: Song,
    lockedSetlistFileName: String?,
    shouldIncludeSetlistAssignments: Boolean = true,
) = ActionsMenu { dismiss ->
    SongActions(
        viewModel = viewModel,
        song = song,
        lockedSetlistFileName = lockedSetlistFileName,
        shouldIncludeSetlistAssignments = shouldIncludeSetlistAssignments,
    ) { title, icon, isEnabled, onClick ->
        ActionsMenuItem(
            title = title,
            icon = icon,
            isEnabled = isEnabled,
            onClick = {
                dismiss()
                onClick()
            },
        )
    }
}
