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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_start
import com.pandulapeter.campfire.presentation.resources.metronome_stop
import com.pandulapeter.campfire.presentation.ui.components.CampfireTooltip
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.metronome.PlayStopMark

/**
 * Play and stop for the click, as the end of the dock's tempo chip and of its metronome panel: the mark turning between
 * the two, taking the press over the row's whole height, M said as its key.
 */
@Composable
internal fun DockPlayStop(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(if (isPlaying) Res.string.metronome_stop else Res.string.metronome_start)
    CampfireTooltip(modifier = modifier, label = label, shortcut = KeyShortcut.METRONOME) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label }
                .padding(horizontal = PLAY_STOP_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            PlayStopMark(isPlaying = isPlaying, size = PLAY_STOP_SIZE)
        }
    }
}

private val PLAY_STOP_PADDING = 8.dp
private val PLAY_STOP_SIZE = 18.dp
