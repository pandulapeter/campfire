/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_start
import com.pandulapeter.campfire.presentation.resources.metronome_stop
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.metronome.PlayStopMark

/**
 * Play and stop for the click, as the end of the dock's tempo chip and of its metronome panel: the mark turning between
 * the two, taking the press over the row's whole height ([DockEndButton]), M said as its key. The tall tempo chip's is
 * [DockRoundPlayStop].
 */
@Composable
internal fun DockPlayStop(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    onClick: () -> Unit,
) = DockEndButton(
    modifier = modifier,
    label = stringResource(if (isPlaying) Res.string.metronome_stop else Res.string.metronome_start),
    shortcut = KeyShortcut.METRONOME,
    onClick = onClick,
) {
    PlayStopMark(isPlaying = isPlaying, size = DOCK_END_MARK_SIZE)
}

/**
 * Play and stop for the click as a round filled button ([DockRoundButton], [size] wide), as the Metronome tab's is: the
 * end of the tall tempo chip beside the diagrams, where there is room for a button a finger finds without looking.
 */
@Composable
internal fun DockRoundPlayStop(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    size: Dp,
    onClick: () -> Unit,
) = DockRoundButton(
    modifier = modifier,
    label = stringResource(if (isPlaying) Res.string.metronome_stop else Res.string.metronome_start),
    shortcut = KeyShortcut.METRONOME,
    size = size,
    onClick = onClick,
) {
    PlayStopMark(isPlaying = isPlaying, size = dockRoundMarkSize(size))
}
