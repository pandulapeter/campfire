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
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.song_details_editing_actions
import org.jetbrains.compose.resources.painterResource

/**
 * Everything that writes the song's file from the song details screen, behind a button of its own next to
 * [SongActions]: the editor, then the sheets that each write one part of what the song says about itself. Kept apart
 * from the song's other actions because there are as many of them as of everything else together, and a reader looking
 * for Export had to read past all of them; the pencil says which of the two menus is which. It never expands, since
 * every entry opens a screen or a sheet that is visited rather than reached for while playing.
 *
 * @param fileEditItems The metadata and cover art editors, put after the editor.
 */
@Composable
internal fun SongEditingActions(
    modifier: Modifier = Modifier,
    actions: SongActionHandler,
    song: Song,
    fileEditItems: List<ActionsMenuItem>,
) = ActionsMenu(
    modifier = modifier,
    contentDescription = stringResource(Res.string.song_details_editing_actions),
    icon = painterResource(Res.drawable.ic_edit),
    isExpandable = false,
    items = listOf(editSongAction(actions = actions, song = song)) + fileEditItems,
)
