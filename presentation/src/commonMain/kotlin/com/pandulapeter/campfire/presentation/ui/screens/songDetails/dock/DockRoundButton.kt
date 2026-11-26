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

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.ui.components.CampfireTooltip
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut

/**
 * A round filled button [size] wide at the end of one of the dock's tall controls beside the diagrams (play and stop on
 * the tempo chip, the tuner on the instrument card), [label] as what it says and [shortcut] as its key: the two cards
 * are built alike, so that the row reads tune, the diagrams, play.
 */
@Composable
internal fun DockRoundButton(
    modifier: Modifier = Modifier,
    label: String,
    shortcut: KeyShortcut,
    size: Dp,
    onClick: () -> Unit,
    mark: @Composable () -> Unit,
) = CampfireTooltip(modifier = modifier, label = label, shortcut = shortcut) {
    FilledIconButton(
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = label },
        onClick = onClick,
    ) {
        mark()
    }
}

/** How large the mark of a [DockRoundButton] [size] wide is drawn: in the same proportion at every size. */
internal fun dockRoundMarkSize(size: Dp) = size * ROUND_MARK_SHARE

/** A 20 dp mark in a 44 dp button. */
private const val ROUND_MARK_SHARE = 20f / 44f
