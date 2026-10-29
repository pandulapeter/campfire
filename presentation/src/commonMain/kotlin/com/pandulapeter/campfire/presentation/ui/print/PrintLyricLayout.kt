/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting
import com.pandulapeter.campfire.presentation.ui.songView.chordLeft
import kotlin.math.max
import kotlin.math.min

/**
 * A row of lyrics as the renderer lays it out, in its own coordinates: [width] wide, and a chord's [anchors] in the order
 * the chords' offsets were asked for.
 */
internal class PrintLyricLayout(
    val width: Float,
    val anchors: List<PrintChordAnchor>,
)

/**
 * Where a chord hangs from: [x], the edge its character starts at in the bidirectional run it lands in, which reads
 * from the right where [isRightToLeft] (`chordLeft`'s anchor).
 */
internal class PrintChordAnchor(
    val x: Float,
    val isRightToLeft: Boolean,
)

/**
 * Lays out [text], a row of lyrics in [style] carrying [formatting], and finds where the chords at [offsets] hang from:
 * what only a text layout can say once a row mixes directions, a Latin word inside a Hebrew line resolving to a run of
 * its own.
 */
internal typealias PrintLyricMeasure = (text: String, style: PrintStyle, formatting: List<Formatting>, offsets: List<Int>) -> PrintLyricLayout

/**
 * The layout of a row that reads one way only, worked out from the widths of its [pieces]: a chord hangs from where
 * the text before its character ends, counted from the left edge, or from the right one in a right-to-left row.
 */
internal fun arithmeticLyricLayout(
    pieces: List<PrintLyricPiece>,
    length: Int,
    offsets: List<Int>,
    isRightToLeft: Boolean,
    measure: (String, PrintStyle) -> Float,
): PrintLyricLayout {
    val width = pieces.widthUntil(length, measure)
    return PrintLyricLayout(
        width = width,
        anchors = offsets.map { offset ->
            val before = pieces.widthUntil(offset.coerceIn(0, length), measure)
            PrintChordAnchor(x = if (isRightToLeft) width - before else before, isRightToLeft = isRightToLeft)
        },
    )
}

/**
 * The x a printed chord [chordWidth] wide goes at on its chord line, [lineWidth] wide, as `chordLeft` places it on
 * screen, or null where it does not fit beside the chord before it, whose edge is [previousChordEdge], and starts the
 * next chord line: a page has room below a lyric for another line of chords, which the screen does not. A chord that is
 * the first of its line and still reaches past the edge it runs towards is sent on as well, to start that edge's next
 * line, where it fits from the start.
 */
internal fun printChordLeft(
    anchor: Float,
    chordWidth: Float,
    lineWidth: Float,
    previousChordEdge: Float,
    isRunRightToLeft: Boolean,
    isLineRightToLeft: Boolean,
): Float? {
    val wanted = if (isRunRightToLeft) anchor - chordWidth else anchor
    val overflows = if (isLineRightToLeft) {
        min(wanted, previousChordEdge - chordWidth).let { it < 0f && it + chordWidth < lineWidth }
    } else {
        max(wanted, previousChordEdge).let { it > 0f && it + chordWidth > lineWidth }
    }
    return if (overflows) {
        null
    } else {
        chordLeft(
            anchor = anchor,
            chordWidth = chordWidth,
            rowWidth = lineWidth,
            previousChordEdge = previousChordEdge,
            isRunRightToLeft = isRunRightToLeft,
            isLineRightToLeft = isLineRightToLeft,
        )
    }
}
