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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
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
import com.pandulapeter.campfire.presentation.resources.songs_delete_song
import com.pandulapeter.campfire.presentation.resources.songs_edit_song
import com.pandulapeter.campfire.presentation.resources.songs_export_song
import com.pandulapeter.campfire.presentation.resources.songs_setlist_assignments
import com.pandulapeter.campfire.presentation.resources.songs_update_file_name
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import org.jetbrains.compose.resources.painterResource

/**
 * The overflow button of a list row and the menu it opens, filled by [content] with one [ActionsMenuItem] per
 * action. [content] is handed the way to choose an entry: every entry acts through it, which closes the menu before
 * the action and ignores a second choice made while the menu is on its way out.
 *
 * Separate from [SongActionsButton] because not every row that wants a menu has a song behind it: a setlist entry
 * whose file has gone missing still has the one action of being taken out of the setlist.
 *
 * [modifier] goes on the button rather than on the menu, which hangs from wherever the button ends up.
 *
 * @param state Whether the menu is open, hoisted by a row that opens the same menu from a long press as well.
 */
@Composable
internal fun ActionsMenu(
    modifier: Modifier = Modifier,
    state: OverflowMenuState = rememberOverflowMenuState(),
    contentDescription: String = stringResource(Res.string.songs_actions),
    content: @Composable (select: (action: () -> Unit) -> Unit) -> Unit,
) = OverflowMenu(
    state = state,
    button = { open ->
        IconButton(
            modifier = modifier,
            onClick = open,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_more),
                contentDescription = contentDescription,
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
 * The overflow button of a song row, or of the song details app bar, and the dropdown menu of everything that can be
 * done to the song. A dropdown on every platform, touch included, since an overflow button is read as the promise of
 * a menu hanging from it - which is what every other overflow button in the app opens, the setlist header's and the
 * editor's among them.
 *
 * The button is there on every platform, because it is the only thing on a row that says the actions exist: the long
 * press that opens the same menu on the songs screen announces itself to nobody, so it is a shortcut for the reader
 * who already knows about it rather than the way in.
 *
 * @param state Whether the menu is open, hoisted by the songs screen, whose rows also open it from a long press.
 * @param lockedSetlistFileName The one setlist the assignments sheet may not take the song out of, because the
 *   screen that opened it is showing the song as part of that setlist and would have the ground pulled from under
 *   it. Null on the setlists screen, where a row is the song's membership of the setlist and giving it up is the
 *   whole point, and null in the library, where no setlist is in play.
 * @param leadingItems Entries that belong to the row rather than to the song, put before the song's own: moving a row
 *   of a setlist up or down.
 */
@Composable
internal fun SongActionsButton(
    modifier: Modifier = Modifier,
    state: OverflowMenuState = rememberOverflowMenuState(),
    viewModel: CampfireViewModel,
    song: Song,
    lockedSetlistFileName: String?,
    leadingItems: @Composable (select: (action: () -> Unit) -> Unit) -> Unit = {},
) {
    val filePicker = LocalFilePicker.current
    ActionsMenu(
        modifier = modifier,
        state = state,
    ) { select ->
        // Collected here rather than by the button: this content is only composed while the menu is open, and the
        // button is in every row of the song list, where a collection of its own is a coroutine and a lifecycle
        // observer per row for a value none of them draws. The state is kept up to date by the view model whether
        // or not anybody collects it, so the menu opens on the right star rather than correcting itself a frame in.
        val songFileNamesInSetlists by viewModel.songFileNamesInSetlists.collectAsStateWithLifecycle()
        leadingItems(select)
        // Each entry acts through `select`, which closes the menu before it acts - so that it is gone by the time the
        // dialog or the picker it opens is on the screen - and only once.
        ActionsMenuItem(
            title = stringResource(Res.string.songs_edit_song),
            icon = painterResource(Res.drawable.ic_edit),
            onClick = { select { viewModel.openEditor(song.fileName) } },
        )
        // One entry for both directions: the sheet it opens is a list of every setlist with a box each, so putting the
        // song into one and taking it out of another are the same gesture there, and a menu that offered them as two
        // separate actions was naming the same sheet twice.
        ActionsMenuItem(
            title = stringResource(Res.string.songs_setlist_assignments),
            icon = painterResource(if (song.fileName in songFileNamesInSetlists) Res.drawable.ic_setlists else Res.drawable.ic_setlists_outline),
            onClick = {
                select {
                    viewModel.showDialog(
                        CampfireViewModel.DialogType.SetlistPicker(song = song, lockedSetlistFileName = lockedSetlistFileName)
                    )
                }
            },
        )
        // Only where it would do something: a file already named after its own metadata, or one with no title to be
        // named after, has nothing to update and the entry would be an offer that never comes to anything.
        if (song.canUpdateFileName) {
            ActionsMenuItem(
                title = stringResource(Res.string.songs_update_file_name),
                icon = painterResource(Res.drawable.ic_rename),
                onClick = { select { viewModel.updateSongFileName(song) } },
            )
        }
        ActionsMenuItem(
            title = stringResource(Res.string.songs_export_song),
            icon = painterResource(Res.drawable.ic_export),
            onClick = { select { viewModel.exportSong(filePicker, song.fileName) } },
        )
        // Only where sending a file is a different thing from saving one, which on desktop and the web it is not.
        if (filePicker.canShare) {
            ActionsMenuItem(
                title = stringResource(Res.string.share),
                icon = painterResource(Res.drawable.ic_share),
                onClick = { select { viewModel.shareSong(filePicker, song.fileName) } },
            )
        }
        ActionsMenuItem(
            title = stringResource(Res.string.songs_delete_song),
            icon = painterResource(Res.drawable.ic_delete),
            onClick = { select { viewModel.showDialog(CampfireViewModel.DialogType.DeleteSong(song)) } },
        )
    }
}
