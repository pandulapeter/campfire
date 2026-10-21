/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.actions

import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.components.menu.OverflowMenuState
import com.pandulapeter.campfire.presentation.ui.components.menu.rememberOverflowMenuState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_duplicate
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.songs_share_song
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_rename
import com.pandulapeter.campfire.presentation.resources.songs_delete_song
import com.pandulapeter.campfire.presentation.resources.songs_duplicate_song
import com.pandulapeter.campfire.presentation.resources.songs_edit_song
import com.pandulapeter.campfire.presentation.resources.songs_update_file_name
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
 * @param state Whether the menu is open, hoisted by the song rows of the songs and setlists screens, which also open it from a long press.
 * @param isDeletable Whether the song can be deleted from here, which it only can where the song is read as part of
 *   the library. A setlist is the list somebody wrote down to play from, and a song reached through one is taken out
 *   of it rather than removed from every setlist and the library at once.
 * @param isDuplicable Whether the menu offers a copy of the song, which only the songs screen's cards do: a copy is a
 *   new song of the library, so it is made where the library's songs are made.
 * The entries are built only while the menu is up (see [ActionsMenu]), and so are [leadingItems] and [fileEditItems]:
 * a list composes one of these for every card it shows.
 *
 * @param leadingItems Actions put before the song's own: the ones that belong to the row rather than to the song
 *   (moving a row of a setlist up or down, and taking it out of the setlist), and on the song details screen whichever
 *   of its bar's buttons the bar has no room for: the setlist assignments and the tuner.
 * @param fileEditItems Entries kept after the editor in the menu: on a song card, the metadata editors (tags and
 *   languages) and then the way into the selection mode.
 * @param isAfterButton Whether a [SetlistAssignmentsButton] stands right before the menu, see [ActionsMenu].
 * @param menuFooter The song details screen's text size stepper, see [ActionsMenu].
 */
@Composable
internal fun SongActions(
    modifier: Modifier = Modifier,
    state: OverflowMenuState = rememberOverflowMenuState(),
    actions: SongActionHandler,
    song: Song,
    isDeletable: Boolean,
    isEditAndExportOnly: Boolean = false,
    isDuplicable: Boolean = false,
    setlistFileName: String? = null,
    leadingItems: @Composable () -> List<ActionsMenuItem> = { emptyList() },
    fileEditItems: @Composable () -> List<ActionsMenuItem> = { emptyList() },
    isAfterButton: Boolean = false,
    menuFooter: (@Composable () -> Unit)? = null,
) = ActionsMenu(
    modifier = modifier,
    state = state,
    isAfterButton = isAfterButton,
    menuFooter = menuFooter,
) {
    val editItems = listOf(editSongAction(actions = actions, song = song)) + if (isEditAndExportOnly) emptyList() else fileEditItems()
    leadingItems() + editItems + listOfNotNull(
        // Only where it would do something: a file already named after its own metadata, or one with no title to
        // be named after, has nothing to update and the action would be an offer that never comes to anything.
        if (song.canUpdateFileName && !isEditAndExportOnly) {
            ActionsMenuItem(
                title = stringResource(Res.string.songs_update_file_name),
                icon = painterResource(Res.drawable.ic_rename),
                isAlwaysInMenu = true,
                onClick = { actions.updateFileName(song) },
            )
        } else {
            null
        },
        if (isDuplicable && !isEditAndExportOnly) {
            ActionsMenuItem(
                title = stringResource(Res.string.songs_duplicate_song),
                icon = painterResource(Res.drawable.ic_duplicate),
                isAlwaysInMenu = true,
                onClick = { actions.duplicate(song) },
            )
        } else {
            null
        },
        ActionsMenuItem(
            title = stringResource(Res.string.songs_share_song),
            icon = painterResource(Res.drawable.ic_export),
            isAlwaysInMenu = true,
            onClick = { actions.export(song, setlistFileName) },
        ),
        if (isDeletable && !isEditAndExportOnly) {
            ActionsMenuItem(
                title = stringResource(Res.string.songs_delete_song),
                icon = painterResource(Res.drawable.ic_delete),
                isAlwaysInMenu = true,
                onClick = { actions.delete(song) },
            )
        } else {
            null
        },
    )
}

@Composable
internal fun editSongAction(
    actions: SongActionHandler,
    song: Song,
) = ActionsMenuItem(
    title = stringResource(Res.string.songs_edit_song),
    icon = painterResource(Res.drawable.ic_edit),
    onClick = { actions.edit(song) },
)
