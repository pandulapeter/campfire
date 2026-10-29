/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

/**
 * Text only: document bytes and document-specific structures never enter the library. [isCampfireExport] is a PDF
 * Campfire printed, which the import reads by the layout it knows.
 */
data class ExtractedDocument(val pages: List<Page>, val isCampfireExport: Boolean = false) {
    data class Page(val lines: List<Line>)

    /**
     * @property baseline Where the line sits on its page, growing downwards, in the page's units; null where the format
     *   has no such position (Word) and for the blank lines put between paragraphs.
     */
    data class Line(val spans: List<Span>, val isHeading: Boolean = false, val baseline: Double? = null)

    /**
     * @property pieces The glyphs a text written in its reading order stands for (a PDF's `ActualText`, which Campfire's
     *   export writes over right-to-left text), in that order, each where it is drawn: a span placed by its ends alone
     *   would no longer say where its characters are. Empty for a span that is what it shows.
     */
    data class Span(
        val text: String,
        val start: Double,
        val end: Double,
        val size: Double,
        val isBold: Boolean = false,
        val isMonospace: Boolean = false,
        val isRaised: Boolean = false,
        val isItalic: Boolean = false,
        val pieces: List<Span> = emptyList(),
    )
}
