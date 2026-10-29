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
 * Positioned text in arbitrary, consistent units. No document format crosses this boundary. [isCampfireExport] is a
 * sheet Campfire printed itself, which is read by the layout it knows rather than guessed at.
 */
public data class ChordSheet(val pages: List<Page>, val originalText: String? = null, val isCampfireExport: Boolean = false) {
    public data class Page(val lines: List<Line>)

    /**
     * @property baseline Where the line's text sits on its page, in the page's units and growing downwards, which tells
     *   rows of one printed line from lines printed one under another; null where the source has no such position (a
     *   Word document, plain text).
     */
    public data class Line(val spans: List<Span>, val isHeading: Boolean = false, val baseline: Double? = null)

    /**
     * @property pieces What a span written in its reading order over text drawn otherwise is made of (a PDF's
     *   `ActualText`), in that order, each where it is drawn, so that its characters can still be found on the page.
     *   Empty for a span that is what it shows.
     */
    public data class Span(
        val text: String,
        val start: Double,
        val end: Double,
        val size: Double = 1.0,
        val isBold: Boolean = false,
        val isMonospace: Boolean = false,
        val isRaised: Boolean = false,
        val isItalic: Boolean = false,
        val pieces: List<Span> = emptyList(),
    )

    public companion object {
        /**
         * [text] laid out in character columns, as a monospace display shows it; one whose letters mostly read from the
         * right as a right-to-left display shows it ([ChordSheetPlainText]).
         */
        public fun ofPlainText(text: String): ChordSheet = ChordSheet(
            pages = listOf(Page(ChordSheetPlainText.lines(text))),
            originalText = text,
        )
    }
}
