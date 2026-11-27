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
import com.pandulapeter.campfire.presentation.ui.components.CampfireTooltip
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut

/**
 * A mark at the end of one of the dock's chip-high controls (play and stop after the tempo chip's divider, the tuner after
 * the instrument pill's), taking the press over the row's whole height, [label] as what it says and [shortcut] as its key.
 */
@Composable
internal fun DockEndButton(
    modifier: Modifier = Modifier,
    label: String,
    shortcut: KeyShortcut,
    onClick: () -> Unit,
    mark: @Composable () -> Unit,
) = CampfireTooltip(modifier = modifier, label = label, shortcut = shortcut) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = END_BUTTON_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        mark()
    }
}

/** How large the mark of a [DockEndButton] is drawn. */
internal val DOCK_END_MARK_SIZE = 18.dp

private val END_BUTTON_PADDING = 8.dp

/** How wide a [DockEndButton] is. */
internal val DOCK_END_BUTTON_WIDTH = DOCK_END_MARK_SIZE + END_BUTTON_PADDING * 2
