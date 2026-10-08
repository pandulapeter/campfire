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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.songs_export_song
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_rename
import com.pandulapeter.campfire.presentation.resources.songs_delete_song
import com.pandulapeter.campfire.presentation.resources.songs_edit_song
import com.pandulapeter.campfire.presentation.resources.songs_update_file_name
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import org.jetbrains.compose.resources.painterResource

/**
 * Everything that can be done to a song, on a song row or in the song details app bar, behind an overflow button: every
 * button in front of it is put there by the screen, which decides what it has the room for. The menu is a dropdown on
 * every platform, touch included,
 * since an overflow button is read as the promise of a menu hanging from it - which is what every other overflow
 * button in the app opens, the setlist header's and the editor's among them.
 *
 * The overflow button is there on every platform, because it is the only thing on a row that says the actions exist:
 * the long press that opens the same menu on the songs screen announces itself to nobody, so it is a shortcut for the
 * reader who already knows about it rather than the way in.
 *
 * The setlist assignments sheet is not among the entries of its own accord: where the song is read as part of the
 * library it has a [SetlistAssignmentsButton] in front of these, which the song details screen moves into the menu as
 * [setlistAssignmentsAction] where its bar runs out of room, and a setlist row puts the same entry among its
 * [leadingItems], after the row's own "Remove from setlist".
 *
 * @param state Whether the menu is open, hoisted by the songs screen, whose rows also open it from a long press.
 * @param isDeletable Whether the song can be deleted from here, which it only can where the song is read as part of
 *   the library. A setlist is the list somebody wrote down to play from, and a song reached through one is taken out
 *   of it rather than removed from every setlist and the library at once.
 * @param leadingItems Actions put before the song's own: the ones that belong to the row rather than to the song
 *   (moving a row of a setlist up or down, and taking it out of the setlist), and on the song details screen whichever
 *   of its bar's buttons the bar has no room for: the metronome and the setlist assignments.
 * @param isEditShown False where the editor and [fileEditItems] have a menu of their own next to this one
 *   ([SongEditingActions]), which leaves this one with what is done to the file as a whole.
 * @param fileEditItems Metadata editors, kept after the editor in the menu: tags and languages on a song card.
 * @param menuFooter The song details screen's text size stepper, see [ActionsMenu].
 */
@Composable
internal fun SongActions(
    modifier: Modifier = Modifier,
    state: OverflowMenuState = rememberOverflowMenuState(),
    viewModel: CampfireViewModel,
    song: Song,
    isDeletable: Boolean,
    isEditAndExportOnly: Boolean = false,
    isEditShown: Boolean = true,
    setlistFileName: String? = null,
    leadingItems: List<ActionsMenuItem> = emptyList(),
    fileEditItems: List<ActionsMenuItem> = emptyList(),
    menuFooter: (@Composable () -> Unit)? = null,
) {
    val editItems = if (isEditShown) listOf(editSongAction(viewModel = viewModel, song = song)) + fileEditItems.takeUnless { isEditAndExportOnly }.orEmpty() else emptyList()
    ActionsMenu(
        modifier = modifier,
        state = state,
        isExpandable = false,
        menuFooter = menuFooter,
        items = leadingItems + editItems + listOfNotNull(
            // Only where it would do something: a file already named after its own metadata, or one with no title to
            // be named after, has nothing to update and the action would be an offer that never comes to anything.
            if (song.canUpdateFileName && !isEditAndExportOnly) {
                ActionsMenuItem(
                    title = stringResource(Res.string.songs_update_file_name),
                    icon = painterResource(Res.drawable.ic_rename),
                    isAlwaysInMenu = true,
                    onClick = { viewModel.updateSongFileName(song) },
                )
            } else {
                null
            },
            ActionsMenuItem(
                title = stringResource(Res.string.songs_export_song),
                icon = painterResource(Res.drawable.ic_export),
                isAlwaysInMenu = true,
                onClick = { viewModel.showDialog(DialogType.Export(song = song, songSetlistFileName = setlistFileName)) },
            ),
            if (isDeletable && !isEditAndExportOnly) {
                ActionsMenuItem(
                    title = stringResource(Res.string.songs_delete_song),
                    icon = painterResource(Res.drawable.ic_delete),
                    isAlwaysInMenu = true,
                    onClick = { viewModel.showDialog(DialogType.DeleteSong(song)) },
                )
            } else {
                null
            },
        ),
    )
}

@Composable
internal fun editSongAction(
    viewModel: CampfireViewModel,
    song: Song,
) = ActionsMenuItem(
    title = stringResource(Res.string.songs_edit_song),
    icon = painterResource(Res.drawable.ic_edit),
    onClick = { viewModel.openEditor(song.fileName) },
)
