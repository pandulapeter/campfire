/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * How far a run of music [extent] pixels wide at its widest moves, as one block, to the end of a line [lineWidth] wide in
 * a right-to-left song ([isAtEnd]), as the PDF moves it: the music itself still reads from the left, so its lines move
 * together and stay in their columns, and a run that fills the line or wraps does not move at all.
 */
internal fun runShiftAtLineEnd(isAtEnd: Boolean, lineWidth: Float, extent: Float) = if (isAtEnd) (lineWidth - extent).coerceAtLeast(0f) else 0f

/**
 * Places what it modifies [runShiftAtLineEnd] further along the line, measured as it would be anyway, so that its
 * wrapping and the sizes it answers the column layout with stay as they are: only where it is drawn moves.
 */
internal fun Modifier.atLineEnd(isAtEnd: Boolean, extent: Int) = if (!isAtEnd) {
    this
} else {
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(runShiftAtLineEnd(isAtEnd = true, lineWidth = placeable.width.toFloat(), extent = extent.toFloat()).toInt(), 0)
        }
    }
}
