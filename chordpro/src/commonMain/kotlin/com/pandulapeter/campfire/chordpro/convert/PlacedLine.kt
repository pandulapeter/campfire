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

/**
 * A line of a sheet with what its place on the page says about it, which its text alone cannot: a section label printed
 * in the margin ([ChordSheetMarginLabels]), or a part of a header line other than its first ([ChordSheetHeaderParts]).
 */
internal data class PlacedLine(val line: ChordSheet.Line, val isMarginLabel: Boolean = false, val isHeaderPart: Boolean = false) {
    internal companion object {
        /** The words of [spans], one space between those standing apart, for judging a part of a line by its text. */
        fun text(spans: List<ChordSheet.Span>) = buildString {
            var previous: ChordSheet.Span? = null
            for (span in spans) {
                if (previous != null && span.start - previous.end > previous.size * 0.1 && lastOrNull() != ' ') append(' ')
                append(span.text)
                previous = span
            }
        }.trim()
    }
}
