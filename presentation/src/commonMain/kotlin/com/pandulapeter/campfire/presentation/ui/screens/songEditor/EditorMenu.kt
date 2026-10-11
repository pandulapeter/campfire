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
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.ic_refresh
import com.pandulapeter.campfire.presentation.resources.ic_open_in_new
import com.pandulapeter.campfire.presentation.resources.ic_prettify
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
import com.pandulapeter.campfire.presentation.resources.song_editor_prettify
import com.pandulapeter.campfire.presentation.resources.song_editor_revert
import com.pandulapeter.campfire.presentation.resources.song_editor_chordpro_reference
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import org.jetbrains.compose.resources.painterResource

/**
 * The actions of the editor that are neither writing the file nor undoing a keystroke, behind the same overflow button
 * the song details screen uses: the About the song sheet over the text being typed — in every pane, so that the song's
 * details are a tap away also while the preview's card that edits them is out of sight — then prettifying, the revert
 * and the ChordPro reference, the one place that explains every directive the Shortcuts write (a `{define}` above all,
 * which is not something to guess the shape of). They stay in the menu however much room the bar has
 * ([ActionsMenuItem.isAlwaysInMenu]), since throwing away everything typed since the last save is not something to end
 * up in by mistapping the button next to Save.
 */
@Composable
internal fun EditorMenu(
    modifier: Modifier = Modifier,
    onOpenSongInfo: () -> Unit,
    canPrettify: () -> Boolean,
    onPrettify: () -> Unit,
    canRevert: Boolean,
    onRevert: () -> Unit,
    onOpenChordProReference: () -> Unit,
) = ActionsMenu(
    modifier = modifier,
    items = listOf(
        ActionsMenuItem(
            title = stringResource(Res.string.song_details_song_info),
            icon = painterResource(Res.drawable.ic_info),
            isAlwaysInMenu = true,
            onClick = onOpenSongInfo,
        ),
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
