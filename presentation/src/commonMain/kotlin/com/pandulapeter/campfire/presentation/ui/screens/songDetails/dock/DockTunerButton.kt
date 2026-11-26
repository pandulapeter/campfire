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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import org.jetbrains.compose.resources.painterResource

/**
 * The way to the tuner sheet at the end of the dock's instrument card, which tunes the strings the card names: a round
 * button [roundSize] wide on the tall card beside the diagrams, as the tempo chip's play and stop is, else (null) the
 * tuner's mark after the chip-high card's divider. U is its key. `Dock: tuner` breadcrumb.
 */
@Composable
internal fun DockTunerButton(
    modifier: Modifier = Modifier,
    roundSize: Dp?,
    onClick: () -> Unit,
) {
    val label = stringResource(Res.string.tuner)
    val onTunerClicked = {
        println("Dock: tuner")
        onClick()
    }
    val mark = @Composable { size: Dp ->
        Icon(modifier = Modifier.size(size), painter = painterResource(Res.drawable.ic_tuner), contentDescription = null)
    }
    if (roundSize != null) {
        DockRoundButton(modifier = modifier, label = label, shortcut = KeyShortcut.TUNER, size = roundSize, onClick = onTunerClicked) {
            mark(dockRoundMarkSize(roundSize))
        }
    } else {
        DockEndButton(modifier = modifier, label = label, shortcut = KeyShortcut.TUNER, onClick = onTunerClicked) {
            mark(DOCK_END_MARK_SIZE)
        }
    }
}
