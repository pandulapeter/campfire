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

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/**
 * How far two icon buttons standing side by side reach into each other's touch targets, which is the one spacing
 * every row of them in the app keeps - a song card's star and dots, the list screens' pill, a setlist header and the
 * bars of the song details and the editor. Side by side at their full touch targets, two icons are twice a button's
 * own padding apart, which reads as two controls that happen to be near each other rather than as one row. The button
 * drawn last is the one a press on the overlap reaches, which still leaves each of them most of its touch target.
 */
internal val ACTION_BUTTON_OVERLAP = 12.dp

/**
 * Lays an action out narrower than it is by [start] and [end], drawing and taking presses past those edges, so that it
 * reaches into its neighbors by that much (see [ACTION_BUTTON_OVERLAP]). By default half the overlap on either side,
 * for a row that pads its ends by that half so its first and last buttons stay where they were.
 *
 * Put outside anything that animates the action's width: while that is narrower than the overlap it takes up no room
 * at all rather than a negative amount, so an action shrinking away leaves the ones after it moving evenly to the end.
 */
internal fun Modifier.overlappingAction(
    start: Dp = ACTION_BUTTON_OVERLAP / 2,
    end: Dp = ACTION_BUTTON_OVERLAP / 2,
) = layout { measurable, constraints ->
    val startPx = start.roundToPx()
    val trim = startPx + end.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    val width = constraints.constrainWidth((placeable.width - trim).coerceAtLeast(0))
    layout(width, placeable.height) {
        val overflow = placeable.width - width
        placeable.placeRelative(x = if (trim == 0) 0 else -overflow * startPx / trim, y = 0)
    }
}
