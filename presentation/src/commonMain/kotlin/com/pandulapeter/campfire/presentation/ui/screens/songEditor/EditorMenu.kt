/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_refresh
import com.pandulapeter.campfire.presentation.resources.ic_open_in_new
import com.pandulapeter.campfire.presentation.resources.ic_prettify
import com.pandulapeter.campfire.presentation.resources.song_editor_prettify
import com.pandulapeter.campfire.presentation.resources.song_editor_revert
import com.pandulapeter.campfire.presentation.resources.song_editor_chordpro_reference
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.coverArtAction
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.songPlayingAction
import org.jetbrains.compose.resources.painterResource

/**
 * The actions of the editor that are neither writing the file nor undoing a keystroke, behind the same overflow button
 * the song details screen uses: the metadata editors of [editingActions] — in every pane, so they are a tap away also
 * while the preview's card that has them as buttons is out of sight — with cover art next to Edit song details where
 * covers are on, then prettifying, the revert and the ChordPro reference, the one place that explains every directive
 * the Shortcuts write (a `{define}` above all, which is not something to guess the shape of). They stay in the menu however much room the bar has ([ActionsMenuItem.isAlwaysInMenu]), since throwing away everything typed since the last save is not
 * something to end up in by mistapping the button next to Save.
 */
@Composable
internal fun EditorMenu(
    modifier: Modifier = Modifier,
    editingActions: List<ActionsMenuItem>,
    songPlayingAction: ActionsMenuItem?,
    coverArtAction: ActionsMenuItem?,
    canPrettify: () -> Boolean,
    onPrettify: () -> Unit,
    canRevert: Boolean,
    onRevert: () -> Unit,
    onOpenChordProReference: () -> Unit,
) = ActionsMenu(
    modifier = modifier,
    // Song defaults and the cover art are put next to Edit metadata, the other editors of the song's header, ahead of
    // the chip groups, in the order the song details editing menu has them.
    items = editingActions.take(1) + listOfNotNull(songPlayingAction, coverArtAction) + editingActions.drop(1) + listOf(
        ActionsMenuItem(
            title = stringResource(Res.string.song_editor_prettify),
            icon = painterResource(Res.drawable.ic_prettify),
            isEnabledInMenu = canPrettify,
            isAlwaysInMenu = true,
            onClick = onPrettify,
        ),
        ActionsMenuItem(
            title = stringResource(Res.string.song_editor_chordpro_reference),
            icon = painterResource(Res.drawable.ic_open_in_new),
            isAlwaysInMenu = true,
            onClick = onOpenChordProReference,
        ),
        ActionsMenuItem(
            title = stringResource(Res.string.song_editor_revert),
            icon = painterResource(Res.drawable.ic_refresh),
            isEnabled = canRevert,
            isAlwaysInMenu = true,
            onClick = onRevert,
        ),
    ),
)
