/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * The [MetronomePanel]'s line: the [button] at its end, and the [beats] at most [beatsMaxWidth] wide, centered on the
 * window between its insets (the page a prominent panel heads is centered there too) rather than on the room the button
 * leaves them, so that they stand over the page's middle; where the window is too narrow for that, they keep [gap] from
 * the button instead.
 *
 * @param startPadding The panel's own padding at its start, the insets left out, which the window's middle is found by.
 * @param endPadding The same at its end.
 */
@Composable
internal fun MetronomePanelRow(
    modifier: Modifier,
    beatsMaxWidth: Dp,
    gap: Dp,
    startPadding: Dp,
    endPadding: Dp,
    beats: @Composable () -> Unit,
    button: @Composable () -> Unit,
) = Layout(
    modifier = modifier,
    // Each in a box of its own, so that there are exactly two children whatever the tooltip around the button emits.
    content = {
        Box { beats() }
        Box { button() }
    },
) { measurables, constraints ->
    val gapPx = gap.roundToPx()
    val buttonPlaceable = measurables[1].measure(constraints.copy(minWidth = 0))
    val room = (constraints.maxWidth - buttonPlaceable.width - gapPx).coerceAtLeast(0)
    val beatsWidth = if (beatsMaxWidth == Dp.Infinity) room else minOf(beatsMaxWidth.roundToPx(), room).coerceAtLeast(0)
    val beatsPlaceable = measurables[0].measure(constraints.copy(minWidth = beatsWidth, maxWidth = beatsWidth))
    val beatsStart = beatRowStart(
        room = room,
        beatsWidth = beatsWidth,
        trailingWidth = gapPx + buttonPlaceable.width,
        startPadding = startPadding.roundToPx(),
        endPadding = endPadding.roundToPx(),
    )
    layout(constraints.maxWidth, constraints.maxHeight) {
        beatsPlaceable.placeRelative(beatsStart, (constraints.maxHeight - beatsPlaceable.height) / 2)
        buttonPlaceable.placeRelative(constraints.maxWidth - buttonPlaceable.width, (constraints.maxHeight - buttonPlaceable.height) / 2)
    }
}

/**
 * Where the beats start in the [room] the button leaves them, measured from its start: centered on the window between
 * its insets, which reaches [startPadding] before the room and [trailingWidth] (the gap and the button) plus [endPadding]
 * after it, but never so far towards the button that they would leave less than the room's end to it.
 */
internal fun beatRowStart(
    room: Int,
    beatsWidth: Int,
    trailingWidth: Int,
    startPadding: Int,
    endPadding: Int,
): Int = ((room + trailingWidth + endPadding - startPadding - beatsWidth) / 2).coerceIn(0, maxOf(room - beatsWidth, 0))
