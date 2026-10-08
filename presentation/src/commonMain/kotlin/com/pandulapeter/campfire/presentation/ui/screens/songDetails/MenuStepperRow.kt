/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A stepper as a row of an overflow menu: the name of what it sets, in a menu entry's style, at its paddings and its
 * height, so that it lines up with the entries under it, and the stepper at the end. The row is not an entry that is
 * chosen and takes no press itself, and the menu stays open while the stepper's buttons are pressed, so that a song is
 * taken up three semitones in three taps with the result in sight.
 */
@Composable
internal fun MenuStepperRow(
    label: String,
    stepper: @Composable () -> Unit,
) = Row(
    modifier = Modifier
        .widthIn(min = MENU_ROW_MIN_WIDTH)
        .height(MENU_ROW_HEIGHT)
        .padding(horizontal = MENU_ROW_HORIZONTAL_PADDING),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        modifier = Modifier
            .weight(1f)
            .padding(end = MENU_ROW_LABEL_GAP),
        text = label,
        style = MaterialTheme.typography.labelLarge,
    )
    stepper()
}

private val MENU_ROW_HEIGHT = 48.dp // A menu entry's own.
private val MENU_ROW_HORIZONTAL_PADDING = 12.dp // A menu entry's own.
private val MENU_ROW_LABEL_GAP = 16.dp
private val MENU_ROW_MIN_WIDTH = 280.dp
