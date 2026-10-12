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

/**
 * Where the thumb of a [FastScroller] dragged by a finger is, as far as the hand is told about it: the item at the top
 * of the list, the label of its section, and the end of the track the thumb is held against, if any.
 */
internal data class FastScrollerDetent(
    val item: Int,
    val label: String?,
    val end: TrackEnd?,
)

internal enum class TrackEnd {
    TOP,
    BOTTOM,
}

/** How strongly a step of the dragged thumb is felt: not at all, as a light tick, or as a notch. */
internal enum class FastScrollerNotch {
    NONE,
    TICK,
    NOTCH,
}

/**
 * What going from [previous] to [current] feels like, the way the teeth of a dial are felt: every row that passes the
 * top of the list is a light tick, so the hand feels the list move at the speed it moves whatever the sections are
 * like, and a new section coming to the top, or the thumb coming up against an end of the track, is a notch. Leaving
 * an end, or scrolling into rows with no label, adds nothing to the tick, since nothing has been arrived at.
 */
internal fun detentNotch(previous: FastScrollerDetent, current: FastScrollerDetent) = when {
    (current.label != null && current.label != previous.label) || (current.end != null && current.end != previous.end) -> FastScrollerNotch.NOTCH
    current.item != previous.item -> FastScrollerNotch.TICK
    else -> FastScrollerNotch.NONE
}
