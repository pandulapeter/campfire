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
 * Where the thumb of a [FastScroller] dragged by a finger is, as far as the hand is told about it: the label of the
 * section at the top of the list, and the end of the track the thumb is held against, if any.
 */
internal data class FastScrollerDetent(
    val label: String?,
    val end: TrackEnd?,
)

internal enum class TrackEnd {
    TOP,
    BOTTOM,
}

/**
 * Whether going from [previous] to [current] is a place the finger should feel, the way the notches of a dial are felt:
 * a new section coming to the top of the list, or the thumb coming up against an end of the track. Leaving an end, or
 * scrolling into rows with no label, is felt as nothing, since nothing has been arrived at.
 */
internal fun isDetentReached(previous: FastScrollerDetent, current: FastScrollerDetent) =
    (current.label != null && current.label != previous.label) || (current.end != null && current.end != previous.end)
