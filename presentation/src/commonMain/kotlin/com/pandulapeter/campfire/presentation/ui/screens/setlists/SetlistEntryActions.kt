/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_move_down
import com.pandulapeter.campfire.presentation.resources.ic_move_up
import com.pandulapeter.campfire.presentation.resources.ic_setlists_remove
import com.pandulapeter.campfire.presentation.resources.setlists_move_down
import com.pandulapeter.campfire.presentation.resources.setlists_move_up
import com.pandulapeter.campfire.presentation.resources.setlists_remove_song
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.components.SongActionHandler
import com.pandulapeter.campfire.presentation.ui.components.SongActions
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.setlistAssignmentsAction
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import org.jetbrains.compose.resources.painterResource

/**
 * The overflow button of one row of a setlist and the actions behind it. A row whose file has gone missing has no
 * song to act on, so it is offered only what still applies to it - moving it and taking it out of the setlist -
 * rather than a list full of entries that would all fail.
 *
 * @param onMoveUp Null where the row cannot move that way, or cannot be moved at all.
 * @param onMoveDown Null where the row cannot move that way, or cannot be moved at all.
 */
@Composable
internal fun SetlistEntryActions(
    viewModel: CampfireViewModel,
    songActions: SongActionHandler,
    entry: SetlistWithSongs.Entry,
    setlistFileName: String,
    isArchived: Boolean,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val onRemove: () -> Unit = {
        viewModel.showDialog(
            DialogType.RemoveSongFromSetlist(
                songFileName = entry.songFileName,
                songTitle = (entry as? SetlistWithSongs.Entry.Present)?.song?.title ?: entry.songFileName,
                setlistFileName = setlistFileName,
            ),
        )
    }
    when (entry) {
        is SetlistWithSongs.Entry.Present -> SongActions(
            actions = songActions,
            song = entry.song,
            isDeletable = false,
            isEditAndExportOnly = isArchived,
            setlistFileName = setlistFileName,
            leadingItems = if (isArchived) {
                emptyList()
            } else {
                // The song is in this setlist by definition, so the star is always filled. This setlist's box is not
                // locked the way the song details screen locks it: unticking it takes the row out from under the sheet,
                // which leaves nothing behind it to pull the reader away from.
                setlistRowActions(onMoveUp, onMoveDown, onRemove) + setlistAssignmentsAction(
                    actions = songActions,
                    song = entry.song,
                    isInSetlist = true,
                )
            },
        )

        is SetlistWithSongs.Entry.Missing -> ActionsMenu(
            isExpandable = false,
            items = setlistRowActions(onMoveUp, onMoveDown, onRemove),
        )
    }
}

/**
 * The actions of a setlist row that are about the row rather than the song in it: the two steps it can be
 * moved by without a drag, for as far as it can go each way, and taking it out of the setlist. The last one carries
 * the setlists tab's list with a minus rather than the bin, since the song stays in the library and every other
 * setlist, and a menu that also offers Delete elsewhere in the app must not have the two read alike.
 */
@Composable
private fun setlistRowActions(
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit,
) = listOfNotNull(
    onMoveUp?.let { onClick ->
        ActionsMenuItem(
            title = stringResource(Res.string.setlists_move_up),
            icon = painterResource(Res.drawable.ic_move_up),
            onClick = onClick,
        )
    },
    onMoveDown?.let { onClick ->
        ActionsMenuItem(
            title = stringResource(Res.string.setlists_move_down),
            icon = painterResource(Res.drawable.ic_move_down),
            onClick = onClick,
        )
    },
    ActionsMenuItem(
        title = stringResource(Res.string.setlists_remove_song),
        icon = painterResource(Res.drawable.ic_setlists_remove),
        onClick = onRemove,
    ),
)
