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

import kotlin.math.max
import kotlin.math.min

/**
 * The x a chord [chordWidth] wide is drawn at on a row [rowWidth] wide.
 *
 * The chord hangs from [anchor], the edge its character starts at in the run it belongs to: rightwards from a character's
 * left edge in a left-to-right run, leftwards from its right edge in a right-to-left one ([isRunRightToLeft]), so a Latin
 * word's chord in a Hebrew line still starts over its first letter. The chords of one row follow one another in the
 * paragraph's direction ([isLineRightToLeft]) whatever runs they land in, since "further along the line" is the
 * paragraph's to say: two chords answering differently would be drawn on top of each other. [previousChordEdge] is the
 * edge the chord must not cross, where it has to start on a row that runs to the right and where it has to end on one
 * that runs to the left.
 *
 * Kept inside the row where that leaves the chord before it alone; where it cannot, the chord stays where it belongs and
 * what reaches past the edge is clipped, since a chord drawn over another one cannot be read at all.
 */
internal fun chordLeft(
    anchor: Float,
    chordWidth: Float,
    rowWidth: Float,
    previousChordEdge: Float,
    isRunRightToLeft: Boolean,
    isLineRightToLeft: Boolean,
): Float {
    val wanted = if (isRunRightToLeft) anchor - chordWidth else anchor
    return if (isLineRightToLeft) {
        min(previousChordEdge - chordWidth, max(min(wanted, previousChordEdge - chordWidth), 0f))
    } else {
        max(previousChordEdge, min(max(wanted, previousChordEdge), max(0f, rowWidth - chordWidth)))
    }
}
