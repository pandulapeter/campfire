/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import kotlin.math.abs

/**
 * What a whole document says about its type, measured once, and the tolerances every reading of a line's size and place
 * shares. [bodySize] is the median size of the document's printed text: the body's, since a sheet prints more lyrics
 * and chords than anything else.
 */
internal data class ChordSheetProfile(val bodySize: Double) {
    /** Whether [size] stands out from the body as a title's does. */
    fun isLarger(size: Double) = size > bodySize * TITLE_RATIO

    companion object {
        /** How much larger than the text around it a title is set, at the least. */
        const val TITLE_RATIO = 1.2

        /** How far apart, as a share of the size, two sizes may be and still be one type's. */
        const val SAME_SIZE = 0.05

        /** How many rows from a song's top its header (title, credits, key, capo) may reach. */
        const val HEADER_LINES = 15

        /** The profile of the document whose text, every line of every page, is [lines]. */
        fun of(lines: List<ChordSheet.Line>): ChordSheetProfile {
            val sizes = lines.flatMap { line -> line.spans.filter { it.text.isNotBlank() }.map { it.size } }.sorted()
            return ChordSheetProfile(sizes.getOrNull(sizes.size / 2) ?: 1.0)
        }

        /** Whether [size] is set in the same type as [reference], judged against [reference]. */
        fun isSameSize(size: Double, reference: Double) = abs(size - reference) < reference * SAME_SIZE

        /** Whether the row at [index] of a song is in its header's reach. */
        fun isInHeader(index: Int) = index < HEADER_LINES

        /**
         * The median size of every span of [lines], blank ones included, which a caller passes without the line it is
         * judging: the type around a candidate title rather than the document's. Null for no span.
         */
        fun medianWithout(lines: List<ChordSheet.Line>): Double? {
            val sizes = lines.flatMap { it.spans }.map { it.size }.sorted()
            return sizes.getOrNull(sizes.size / 2)
        }
    }
}
